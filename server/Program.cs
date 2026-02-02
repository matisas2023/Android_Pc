using System.Collections.Concurrent;
using System.ComponentModel;
using System.Diagnostics;
using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Runtime.InteropServices;
using System.Text.Json;
using System.Text.Json.Serialization;
using System.Management;
using Microsoft.AspNetCore.Http.Json;

var builder = WebApplication.CreateBuilder(args);

builder.Services.Configure<JsonOptions>(options =>
{
    options.SerializerOptions.PropertyNamingPolicy = JsonNamingPolicy.CamelCase;
    options.SerializerOptions.DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull;
});

builder.Services.AddSingleton<SessionStore>();
builder.Services.AddSingleton<RecordingStore>();
builder.Services.AddSingleton<MetricsStore>();
builder.Services.AddHostedService<DiscoveryService>();
builder.Services.AddHostedService<SessionSweepService>();
builder.Services.AddHostedService<MetricsSamplingService>();

var app = builder.Build();

app.Use(async (context, next) =>
{
    if (context.Request.Path.Equals("/auth", StringComparison.OrdinalIgnoreCase))
    {
        await next();
        return;
    }

    var token = TokenHelper.ExtractToken(context.Request);
    var expected = TokenHelper.GetConfiguredToken();
    if (string.IsNullOrWhiteSpace(token) || token != expected)
    {
        context.Response.StatusCode = StatusCodes.Status401Unauthorized;
        await context.Response.WriteAsJsonAsync(new { detail = "Invalid or missing API token." });
        return;
    }

    await next();
});

app.MapPost("/auth", (AuthRequest request) =>
{
    var isValid = request.Token == TokenHelper.GetConfiguredToken();
    if (!isValid)
    {
        return Results.Json(new { detail = "Invalid token" }, statusCode: StatusCodes.Status401Unauthorized);
    }

    return Results.Ok(new { status = "ok" });
});

app.MapPost("/mouse/move", (MouseMoveRequest request) =>
{
    if (request.Absolute)
    {
        InputController.MoveMouseAbsolute(request.X, request.Y, request.Duration);
    }
    else
    {
        InputController.MoveMouseRelative(request.X, request.Y, request.Duration);
    }

    return Results.Ok(new { status = "ok" });
});

app.MapPost("/mouse/click", (MouseClickRequest request) =>
{
    InputController.MouseClick(request);
    return Results.Ok(new { status = "ok" });
});

app.MapPost("/keyboard/press", (KeyboardPressRequest request) =>
{
    InputController.KeyboardPress(request);
    return Results.Ok(new { status = "ok" });
});

app.MapPost("/system/volume", (SystemVolumeRequest request) =>
{
    InputController.AdjustVolume(request.Action, request.Steps);
    return Results.Ok(new { status = "ok" });
});

app.MapPost("/system/launch", (SystemLaunchRequest request) =>
{
    try
    {
        var startInfo = new ProcessStartInfo(request.Command)
        {
            UseShellExecute = false,
        };
        if (request.Args is { Count: > 0 })
        {
            foreach (var arg in request.Args)
            {
                startInfo.ArgumentList.Add(arg);
            }
        }

        Process.Start(startInfo);
    }
    catch (Exception ex) when (ex is Win32Exception or FileNotFoundException)
    {
        return Results.NotFound(new { detail = ex.Message });
    }

    return Results.Ok(new { status = "ok" });
});

app.MapGet("/system/status", () =>
{
    var cpu = SystemMetrics.GetCpuUsage();
    var memory = SystemMetrics.GetMemoryStatus();

    return Results.Ok(new
    {
        cpuPercent = cpu,
        memory = new
        {
            total = memory.Total,
            available = memory.Available,
            used = memory.Used,
            percent = memory.Percent,
        },
    });
});

app.MapGet("/system/metrics", (MetricsStore metrics) =>
{
    return Results.Ok(metrics.GetSnapshot());
});

app.MapGet("/screen/screenshot", () =>
{
    var bytes = ScreenCapture.CapturePng();
    return Results.Bytes(bytes, "image/png");
});

app.MapPost("/session/start", (SessionStartRequest request, SessionStore sessions) =>
{
    var session = sessions.CreateSession(request);
    return Results.Ok(session);
});

app.MapPost("/session/heartbeat", (SessionHeartbeatRequest request, SessionStore sessions) =>
{
    var result = sessions.TouchSession(request.SessionId);
    return result.Status switch
    {
        SessionTouchStatus.NotFound => Results.NotFound(new { detail = "Session not found." }),
        SessionTouchStatus.Expired => Results.Json(new { detail = "Session expired." }, statusCode: StatusCodes.Status410Gone),
        _ => Results.Ok(result.Response),
    };
});

app.MapPost("/session/end", (SessionEndRequest request, SessionStore sessions) =>
{
    var removed = sessions.EndSession(request.SessionId);
    return removed
        ? Results.Ok(new { status = "ok" })
        : Results.NotFound(new { detail = "Session not found." });
});

app.MapGet("/session/status/{sessionId}", (string sessionId, SessionStore sessions) =>
{
    var result = sessions.TouchSession(sessionId);
    return result.Status switch
    {
        SessionTouchStatus.NotFound => Results.NotFound(new { detail = "Session not found." }),
        SessionTouchStatus.Expired => Results.Json(new { detail = "Session expired." }, statusCode: StatusCodes.Status410Gone),
        _ => Results.Ok(result.Response),
    };
});

app.MapPost("/system/power", (SystemPowerRequest request) =>
{
    var ok = SystemPower.Execute(request.Action);
    return ok
        ? Results.Ok(new { status = "ok", action = request.Action })
        : Results.BadRequest(new { detail = "Unsupported action." });
});

app.MapPost("/system/volume/set", (SystemVolumeSetRequest request) =>
{
    if (!OperatingSystem.IsWindows())
    {
        return Results.StatusCode(StatusCodes.Status501NotImplemented);
    }

    var level = Math.Clamp(request.Level, 0, 100);
    AudioController.SetMasterVolume(level / 100f);
    return Results.Ok(new { status = "ok", level });
});

app.MapGet("/screen/stream", async (HttpContext context, int fps = 5) =>
{
    context.Response.ContentType = $"multipart/x-mixed-replace; boundary={AppConstants.StreamBoundary}";

    await foreach (var frame in ScreenCapture.StreamFrames(fps, context.RequestAborted))
    {
        if (context.RequestAborted.IsCancellationRequested)
        {
            break;
        }

        await context.Response.Body.WriteAsync(frame, context.RequestAborted);
        await context.Response.Body.FlushAsync(context.RequestAborted);
    }
});

app.MapGet("/camera/stream", () =>
{
    return Results.Problem("Camera streaming requires additional setup.", statusCode: StatusCodes.Status503ServiceUnavailable);
});

app.MapGet("/camera/photo", () =>
{
    return Results.Problem("Camera capture requires additional setup.", statusCode: StatusCodes.Status503ServiceUnavailable);
});

app.MapPost("/screen/record/start", (ScreenRecordStartRequest request, RecordingStore recordings) =>
{
    var recording = recordings.StartRecording(request);
    return Results.Ok(new { status = "ok", recordingId = recording.Id });
});

app.MapPost("/screen/record/stop/{recordingId}", (string recordingId, RecordingStore recordings) =>
{
    var stopped = recordings.StopRecording(recordingId);
    return stopped
        ? Results.Ok(new { status = "ok", recordingId })
        : Results.NotFound(new { detail = "Recording not found." });
});

app.MapGet("/screen/recordings", (RecordingStore recordings) =>
{
    return Results.Ok(new { recordings = recordings.GetStatus() });
});

app.MapGet("/health", () => Results.Ok(new { status = "ok" }));

app.Urls.Clear();
app.Urls.Add($"http://0.0.0.0:{AppConstants.ServerPort}");

app.Run();

static class AppConstants
{
    public const string ApiTokenEnv = "PC_REMOTE_API_TOKEN";
    public const string DefaultApiToken = "change-me";
    public const int DiscoveryPort = 9999;
    public const string DiscoveryMessage = "PC_REMOTE_DISCOVERY";
    public const int ServerPort = 8000;
    public const string StreamBoundary = "frame";
    public const int SessionSweepIntervalSeconds = 30;
}

record AuthRequest(string Token);
record MouseMoveRequest(int X, int Y, double Duration = 0, bool Absolute = true);
record MouseClickRequest(string Button = "left", int Clicks = 1, double Interval = 0, int? X = null, int? Y = null);
record KeyboardPressRequest(string? Key = null, List<string>? Keys = null, int Presses = 1, double Interval = 0);
record SystemVolumeRequest(string Action, int Steps = 1);
record SystemVolumeSetRequest(int Level);
record SystemLaunchRequest(string Command, List<string>? Args = null);
record SessionStartRequest(string? ClientName = null, int TimeoutSeconds = 900);
record SessionHeartbeatRequest(string SessionId);
record SessionEndRequest(string SessionId);
record SystemPowerRequest(string Action);
record ScreenRecordStartRequest(int Fps = 10, int? DurationSeconds = null);

static class TokenHelper
{
    public static string GetConfiguredToken()
    {
        var token = Environment.GetEnvironmentVariable(AppConstants.ApiTokenEnv);
        return string.IsNullOrWhiteSpace(token) ? AppConstants.DefaultApiToken : token;
    }

    public static string? ExtractToken(HttpRequest request)
    {
        if (request.Headers.TryGetValue("Authorization", out var authHeader))
        {
            var value = authHeader.ToString();
            if (value.StartsWith("Bearer ", StringComparison.OrdinalIgnoreCase))
            {
                return value.Substring("Bearer ".Length).Trim();
            }
        }

        if (request.Headers.TryGetValue("X-API-Token", out var tokenHeader))
        {
            return tokenHeader.ToString();
        }

        return null;
    }
}

sealed class SessionStore
{
    private readonly ConcurrentDictionary<string, SessionInfo> _sessions = new();

    public SessionResponse CreateSession(SessionStartRequest request)
    {
        var id = Guid.NewGuid().ToString();
        var timeout = Math.Clamp(request.TimeoutSeconds, 30, 24 * 60 * 60);
        var now = DateTimeOffset.UtcNow;
        var info = new SessionInfo(id, request.ClientName ?? "unknown", now, now, timeout);
        _sessions[id] = info;
        return new SessionResponse(id, now.AddSeconds(timeout));
    }

    public SessionTouchResult TouchSession(string sessionId)
    {
        if (!_sessions.TryGetValue(sessionId, out var info))
        {
            return new SessionTouchResult(SessionTouchStatus.NotFound, null);
        }

        var now = DateTimeOffset.UtcNow;
        if (now - info.LastSeen > TimeSpan.FromSeconds(info.TimeoutSeconds))
        {
            _sessions.TryRemove(sessionId, out _);
            return new SessionTouchResult(SessionTouchStatus.Expired, null);
        }

        info.LastSeen = now;
        return new SessionTouchResult(
            SessionTouchStatus.Active,
            new SessionResponse(sessionId, now.AddSeconds(info.TimeoutSeconds)));
    }

    public bool EndSession(string sessionId)
    {
        return _sessions.TryRemove(sessionId, out _);
    }

    public IEnumerable<SessionInfo> GetExpiredSessions(DateTimeOffset now)
    {
        foreach (var session in _sessions.Values)
        {
            if (now - session.LastSeen > TimeSpan.FromSeconds(session.TimeoutSeconds))
            {
                yield return session;
            }
        }
    }

    public void RemoveSession(string sessionId)
    {
        _sessions.TryRemove(sessionId, out _);
    }
}

sealed class SessionInfo
{
    public SessionInfo(string id, string clientName, DateTimeOffset createdAt, DateTimeOffset lastSeen, int timeoutSeconds)
    {
        Id = id;
        ClientName = clientName;
        CreatedAt = createdAt;
        LastSeen = lastSeen;
        TimeoutSeconds = timeoutSeconds;
    }

    public string Id { get; }
    public string ClientName { get; }
    public DateTimeOffset CreatedAt { get; }
    public DateTimeOffset LastSeen { get; set; }
    public int TimeoutSeconds { get; }
}

record SessionResponse(string SessionId, DateTimeOffset ExpiresAt);

record SessionTouchResult(SessionTouchStatus Status, SessionResponse? Response);

enum SessionTouchStatus
{
    Active,
    Expired,
    NotFound,
}

sealed class SessionSweepService(SessionStore sessions) : BackgroundService
{
    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        while (!stoppingToken.IsCancellationRequested)
        {
            var now = DateTimeOffset.UtcNow;
            foreach (var session in sessions.GetExpiredSessions(now))
            {
                sessions.RemoveSession(session.Id);
            }

            try
            {
                await Task.Delay(TimeSpan.FromSeconds(AppConstants.SessionSweepIntervalSeconds), stoppingToken);
            }
            catch (TaskCanceledException)
            {
                return;
            }
        }
    }
}

sealed class DiscoveryService(ILogger<DiscoveryService> logger) : BackgroundService
{
    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        using var udp = new UdpClient(AppConstants.DiscoveryPort);
        udp.EnableBroadcast = true;
        logger.LogInformation("Discovery listener started on UDP {Port}", AppConstants.DiscoveryPort);

        while (!stoppingToken.IsCancellationRequested)
        {
            UdpReceiveResult result;
            try
            {
                result = await udp.ReceiveAsync(stoppingToken);
            }
            catch (OperationCanceledException)
            {
                break;
            }

            var message = System.Text.Encoding.UTF8.GetString(result.Buffer).Trim();
            if (!string.Equals(message, AppConstants.DiscoveryMessage, StringComparison.Ordinal))
            {
                continue;
            }

            var payload = JsonSerializer.Serialize(new
            {
                port = AppConstants.ServerPort,
                token = TokenHelper.GetConfiguredToken(),
                ips = NetworkHelper.GetLocalIps(),
            });

            var bytes = System.Text.Encoding.UTF8.GetBytes(payload);
            await udp.SendAsync(bytes, bytes.Length, result.RemoteEndPoint);
        }
    }
}

static class NetworkHelper
{
    public static List<string> GetLocalIps()
    {
        var ips = new HashSet<string>();

        foreach (var networkInterface in NetworkInterface.GetAllNetworkInterfaces())
        {
            if (networkInterface.OperationalStatus != OperationalStatus.Up)
            {
                continue;
            }

            foreach (var address in networkInterface.GetIPProperties().UnicastAddresses)
            {
                if (address.Address.AddressFamily == AddressFamily.InterNetwork)
                {
                    var ip = address.Address.ToString();
                    if (!ip.StartsWith("127.", StringComparison.Ordinal))
                    {
                        ips.Add(ip);
                    }
                }
            }
        }

        return ips.Count > 0 ? ips.ToList() : new List<string> { "127.0.0.1" };
    }
}

static class SystemMetrics
{
    private static readonly PerformanceCounter CpuCounter = new("Processor", "% Processor Time", "_Total");

    public static double GetCpuUsage()
    {
        try
        {
            _ = CpuCounter.NextValue();
            Thread.Sleep(100);
            return Math.Round(CpuCounter.NextValue(), 2);
        }
        catch
        {
            return 0;
        }
    }

    public static MemoryStatus GetMemoryStatus()
    {
        var status = new MEMORYSTATUSEX { dwLength = (uint)Marshal.SizeOf<MEMORYSTATUSEX>() };
        GlobalMemoryStatusEx(ref status);
        var total = (long)status.ullTotalPhys;
        var available = (long)status.ullAvailPhys;
        var used = total - available;
        var percent = total == 0 ? 0 : (double)used / total * 100;
        return new MemoryStatus(total, available, used, Math.Round(percent, 2));
    }

    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Auto)]
    private struct MEMORYSTATUSEX
    {
        public uint dwLength;
        public uint dwMemoryLoad;
        public ulong ullTotalPhys;
        public ulong ullAvailPhys;
        public ulong ullTotalPageFile;
        public ulong ullAvailPageFile;
        public ulong ullTotalVirtual;
        public ulong ullAvailVirtual;
        public ulong ullAvailExtendedVirtual;
    }

    [DllImport("kernel32.dll", CharSet = CharSet.Auto, SetLastError = true)]
    private static extern bool GlobalMemoryStatusEx(ref MEMORYSTATUSEX lpBuffer);
}

record MemoryStatus(long Total, long Available, long Used, double Percent);

static class ScreenCapture
{
    public static byte[] CapturePng()
    {
        using var bitmap = CaptureBitmap();
        using var stream = new MemoryStream();
        bitmap.Save(stream, System.Drawing.Imaging.ImageFormat.Png);
        return stream.ToArray();
    }

    public static async IAsyncEnumerable<byte[]> StreamFrames(int fps, [System.Runtime.CompilerServices.EnumeratorCancellation] CancellationToken token)
    {
        fps = Math.Clamp(fps, 1, 30);
        var delay = TimeSpan.FromSeconds(1.0 / fps);
        while (!token.IsCancellationRequested)
        {
            var frame = CapturePng();
            var header = $"--{AppConstants.StreamBoundary}\r\nContent-Type: image/png\r\n\r\n";
            var footer = "\r\n";
            var headerBytes = System.Text.Encoding.UTF8.GetBytes(header);
            var footerBytes = System.Text.Encoding.UTF8.GetBytes(footer);

            var payload = new byte[headerBytes.Length + frame.Length + footerBytes.Length];
            Buffer.BlockCopy(headerBytes, 0, payload, 0, headerBytes.Length);
            Buffer.BlockCopy(frame, 0, payload, headerBytes.Length, frame.Length);
            Buffer.BlockCopy(footerBytes, 0, payload, headerBytes.Length + frame.Length, footerBytes.Length);

            yield return payload;

            try
            {
                await Task.Delay(delay, token);
            }
            catch (TaskCanceledException)
            {
                yield break;
            }
        }
    }

    private static System.Drawing.Bitmap CaptureBitmap()
    {
        var bounds = System.Windows.Forms.Screen.PrimaryScreen?.Bounds ?? new System.Drawing.Rectangle(0, 0, 1920, 1080);
        var bitmap = new System.Drawing.Bitmap(bounds.Width, bounds.Height);
        using var graphics = System.Drawing.Graphics.FromImage(bitmap);
        graphics.CopyFromScreen(bounds.Left, bounds.Top, 0, 0, bounds.Size);
        return bitmap;
    }
}

static class InputController
{
    public static void MoveMouseAbsolute(int x, int y, double duration)
    {
        if (duration > 0)
        {
            Thread.Sleep(TimeSpan.FromSeconds(duration));
        }

        var screen = System.Windows.Forms.Screen.PrimaryScreen?.Bounds ?? new System.Drawing.Rectangle(0, 0, 1920, 1080);
        var normalizedX = (int)Math.Round(x * 65535.0 / screen.Width);
        var normalizedY = (int)Math.Round(y * 65535.0 / screen.Height);
        SendMouseInput(normalizedX, normalizedY, MouseEventFlags.Move | MouseEventFlags.Absolute);
    }

    public static void MoveMouseRelative(int x, int y, double duration)
    {
        if (duration > 0)
        {
            Thread.Sleep(TimeSpan.FromSeconds(duration));
        }

        SendMouseInput(x, y, MouseEventFlags.Move);
    }

    public static void MouseClick(MouseClickRequest request)
    {
        for (var i = 0; i < request.Clicks; i++)
        {
            if (request.X.HasValue && request.Y.HasValue)
            {
                MoveMouseAbsolute(request.X.Value, request.Y.Value, 0);
            }

            var flags = request.Button switch
            {
                "right" => MouseEventFlags.RightDown | MouseEventFlags.RightUp,
                "middle" => MouseEventFlags.MiddleDown | MouseEventFlags.MiddleUp,
                _ => MouseEventFlags.LeftDown | MouseEventFlags.LeftUp,
            };

            SendMouseInput(0, 0, flags);
            if (request.Interval > 0)
            {
                Thread.Sleep(TimeSpan.FromSeconds(request.Interval));
            }
        }
    }

    public static void KeyboardPress(KeyboardPressRequest request)
    {
        if (request.Keys is { Count: > 0 })
        {
            foreach (var key in request.Keys)
            {
                SendKey(key, true);
            }

            for (var i = request.Keys.Count - 1; i >= 0; i--)
            {
                SendKey(request.Keys[i], false);
            }

            return;
        }

        if (string.IsNullOrWhiteSpace(request.Key))
        {
            return;
        }

        for (var i = 0; i < request.Presses; i++)
        {
            SendKey(request.Key, true);
            SendKey(request.Key, false);
            if (request.Interval > 0)
            {
                Thread.Sleep(TimeSpan.FromSeconds(request.Interval));
            }
        }
    }

    public static void AdjustVolume(string action, int steps)
    {
        var key = action switch
        {
            "up" => "volumeup",
            "down" => "volumedown",
            "mute" => "volumemute",
            _ => string.Empty,
        };

        if (string.IsNullOrEmpty(key))
        {
            return;
        }

        for (var i = 0; i < steps; i++)
        {
            SendKey(key, true);
            SendKey(key, false);
        }
    }

    private static void SendKey(string key, bool keyDown)
    {
        if (!KeyMap.TryGetValue(key.ToLowerInvariant(), out var vk))
        {
            return;
        }

        var input = new INPUT
        {
            type = InputType.Keyboard,
            U = new InputUnion
            {
                ki = new KEYBDINPUT
                {
                    wVk = vk,
                    dwFlags = keyDown ? 0u : KEYEVENTF_KEYUP,
                },
            },
        };

        SendInput(1, new[] { input }, Marshal.SizeOf<INPUT>());
    }

    private static void SendMouseInput(int x, int y, MouseEventFlags flags)
    {
        var input = new INPUT
        {
            type = InputType.Mouse,
            U = new InputUnion
            {
                mi = new MOUSEINPUT
                {
                    dx = x,
                    dy = y,
                    dwFlags = flags,
                },
            },
        };

        SendInput(1, new[] { input }, Marshal.SizeOf<INPUT>());
    }

    private static readonly Dictionary<string, ushort> KeyMap = new(StringComparer.OrdinalIgnoreCase)
    {
        ["enter"] = 0x0D,
        ["esc"] = 0x1B,
        ["escape"] = 0x1B,
        ["tab"] = 0x09,
        ["space"] = 0x20,
        ["backspace"] = 0x08,
        ["delete"] = 0x2E,
        ["up"] = 0x26,
        ["down"] = 0x28,
        ["left"] = 0x25,
        ["right"] = 0x27,
        ["ctrl"] = 0x11,
        ["shift"] = 0x10,
        ["alt"] = 0x12,
        ["f1"] = 0x70,
        ["f2"] = 0x71,
        ["f3"] = 0x72,
        ["f4"] = 0x73,
        ["f5"] = 0x74,
        ["f6"] = 0x75,
        ["f7"] = 0x76,
        ["f8"] = 0x77,
        ["f9"] = 0x78,
        ["f10"] = 0x79,
        ["f11"] = 0x7A,
        ["f12"] = 0x7B,
        ["volumeup"] = 0xAF,
        ["volumedown"] = 0xAE,
        ["volumemute"] = 0xAD,
    };

    private const uint KEYEVENTF_KEYUP = 0x0002;

    private enum InputType : uint
    {
        Mouse = 0,
        Keyboard = 1,
    }

    [Flags]
    private enum MouseEventFlags : uint
    {
        Move = 0x0001,
        LeftDown = 0x0002,
        LeftUp = 0x0004,
        RightDown = 0x0008,
        RightUp = 0x0010,
        MiddleDown = 0x0020,
        MiddleUp = 0x0040,
        Absolute = 0x8000,
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct INPUT
    {
        public InputType type;
        public InputUnion U;
    }

    [StructLayout(LayoutKind.Explicit)]
    private struct InputUnion
    {
        [FieldOffset(0)]
        public MOUSEINPUT mi;
        [FieldOffset(0)]
        public KEYBDINPUT ki;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct MOUSEINPUT
    {
        public int dx;
        public int dy;
        public uint mouseData;
        public MouseEventFlags dwFlags;
        public uint time;
        public IntPtr dwExtraInfo;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct KEYBDINPUT
    {
        public ushort wVk;
        public ushort wScan;
        public uint dwFlags;
        public uint time;
        public IntPtr dwExtraInfo;
    }

    [DllImport("user32.dll", SetLastError = true)]
    private static extern uint SendInput(uint nInputs, INPUT[] pInputs, int cbSize);
}

static class SystemPower
{
    public static bool Execute(string action)
    {
        if (!OperatingSystem.IsWindows())
        {
            return false;
        }

        string[]? command = action switch
        {
            "shutdown" => new[] { "shutdown", "/s", "/t", "0" },
            "restart" => new[] { "shutdown", "/r", "/t", "0" },
            "lock" => new[] { "rundll32.exe", "user32.dll,LockWorkStation" },
            "logoff" => new[] { "shutdown", "/l" },
            "sleep" => new[] { "rundll32.exe", "powrprof.dll,SetSuspendState", "0,1,0" },
            "hibernate" => new[] { "shutdown", "/h" },
            _ => null,
        };

        if (command is null)
        {
            return false;
        }

        var process = new ProcessStartInfo(command[0])
        {
            UseShellExecute = false,
        };
        for (var i = 1; i < command.Length; i++)
        {
            process.ArgumentList.Add(command[i]);
        }

        Process.Start(process);
        return true;
    }
}

static class AudioController
{
    public static void SetMasterVolume(float level)
    {
        var clamped = Math.Clamp(level, 0f, 1f);
        var enumerator = new MMDeviceEnumerator() as IMMDeviceEnumerator;
        if (enumerator == null)
        {
            return;
        }

        Marshal.ThrowExceptionForHR(enumerator.GetDefaultAudioEndpoint(EDataFlow.eRender, ERole.eMultimedia, out var device));
        var audioEndpointVolumeGuid = IAudioEndpointVolumeGuid;
        Marshal.ThrowExceptionForHR(device.Activate(ref audioEndpointVolumeGuid, CLSCTX_ALL, IntPtr.Zero, out var volumeObject));
        var volume = (IAudioEndpointVolume)volumeObject;
        Marshal.ThrowExceptionForHR(volume.SetMasterVolumeLevelScalar(clamped, Guid.Empty));
    }

    private static readonly Guid IAudioEndpointVolumeGuid = typeof(IAudioEndpointVolume).GUID;
    private const int CLSCTX_ALL = 23;

    private enum EDataFlow
    {
        eRender,
        eCapture,
        eAll,
    }

    private enum ERole
    {
        eConsole,
        eMultimedia,
        eCommunications,
    }

    [ComImport]
    [Guid("BCDE0395-E52F-467C-8E3D-C4579291692E")]
    private class MMDeviceEnumerator
    {
    }

    [ComImport]
    [Guid("A95664D2-9614-4F35-A746-DE8DB63617E6")]
    [InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    private interface IMMDeviceEnumerator
    {
        int EnumAudioEndpoints(EDataFlow dataFlow, int dwStateMask, out object ppDevices);
        int GetDefaultAudioEndpoint(EDataFlow dataFlow, ERole role, out IMMDevice ppEndpoint);
        int GetDevice(string pwstrId, out IMMDevice ppDevice);
        int RegisterEndpointNotificationCallback(IntPtr pClient);
        int UnregisterEndpointNotificationCallback(IntPtr pClient);
    }

    [ComImport]
    [Guid("D666063F-1587-4E43-81F1-B948E807363F")]
    [InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    private interface IMMDevice
    {
        int Activate(ref Guid iid, int dwClsCtx, IntPtr pActivationParams, out object ppInterface);
    }

    [ComImport]
    [Guid("5CDF2C82-841E-4546-9722-0CF74078229A")]
    [InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    private interface IAudioEndpointVolume
    {
        int RegisterControlChangeNotify(IntPtr pNotify);
        int UnregisterControlChangeNotify(IntPtr pNotify);
        int GetChannelCount(out int pnChannelCount);
        int SetMasterVolumeLevel(float fLevelDB, Guid pguidEventContext);
        int SetMasterVolumeLevelScalar(float fLevel, Guid pguidEventContext);
        int GetMasterVolumeLevel(out float pfLevelDB);
        int GetMasterVolumeLevelScalar(out float pfLevel);
        int SetChannelVolumeLevel(uint nChannel, float fLevelDB, Guid pguidEventContext);
        int SetChannelVolumeLevelScalar(uint nChannel, float fLevel, Guid pguidEventContext);
        int GetChannelVolumeLevel(uint nChannel, out float pfLevelDB);
        int GetChannelVolumeLevelScalar(uint nChannel, out float pfLevel);
        int SetMute(bool bMute, Guid pguidEventContext);
        int GetMute(out bool pbMute);
        int GetVolumeStepInfo(out uint pnStep, out uint pnStepCount);
        int VolumeStepUp(Guid pguidEventContext);
        int VolumeStepDown(Guid pguidEventContext);
        int QueryHardwareSupport(out uint pdwHardwareSupportMask);
        int GetVolumeRange(out float pflVolumeMindB, out float pflVolumeMaxdB, out float pflVolumeIncrementdB);
    }
}

sealed class RecordingStore
{
    private readonly ConcurrentDictionary<string, RecordingInfo> _recordings = new();
    private readonly string _recordingDirectory;

    public RecordingStore()
    {
        _recordingDirectory = Path.Combine(AppContext.BaseDirectory, "recordings");
        Directory.CreateDirectory(_recordingDirectory);
    }

    public RecordingInfo StartRecording(ScreenRecordStartRequest request)
    {
        var id = Guid.NewGuid().ToString();
        var info = new RecordingInfo
        {
            Id = id,
            File = Path.Combine(_recordingDirectory, $"screen_{id}.mpng"),
            StartedAt = DateTimeOffset.UtcNow,
            Fps = Math.Clamp(request.Fps, 1, 30),
            DurationSeconds = request.DurationSeconds,
        };

        var cts = new CancellationTokenSource();
        info.StopToken = cts;
        _recordings[id] = info;

        _ = Task.Run(() => RecordTask(info, cts.Token));

        return info;
    }

    public bool StopRecording(string recordingId)
    {
        if (!_recordings.TryGetValue(recordingId, out var info))
        {
            return false;
        }

        info.StopToken?.Cancel();
        return true;
    }

    public IDictionary<string, object> GetStatus()
    {
        return _recordings.ToDictionary(
            item => item.Key,
            item => (object)new
            {
                startedAt = item.Value.StartedAt,
                fps = item.Value.Fps,
                durationSeconds = item.Value.DurationSeconds,
                completed = item.Value.Completed,
                file = item.Value.File,
            });
    }

    private async Task RecordTask(RecordingInfo info, CancellationToken token)
    {
        var delay = TimeSpan.FromSeconds(1.0 / info.Fps);
        var deadline = info.DurationSeconds.HasValue
            ? info.StartedAt.AddSeconds(info.DurationSeconds.Value)
            : (DateTimeOffset?)null;

        await using var stream = new FileStream(info.File, FileMode.Append, FileAccess.Write, FileShare.Read);

        try
        {
            while (!token.IsCancellationRequested)
            {
                var frame = ScreenCapture.CapturePng();
                var header = $"--{AppConstants.StreamBoundary}\r\nContent-Type: image/png\r\n\r\n";
                var footer = "\r\n";

                await stream.WriteAsync(System.Text.Encoding.UTF8.GetBytes(header), token);
                await stream.WriteAsync(frame, token);
                await stream.WriteAsync(System.Text.Encoding.UTF8.GetBytes(footer), token);
                await stream.FlushAsync(token);

                if (deadline.HasValue && DateTimeOffset.UtcNow >= deadline.Value)
                {
                    break;
                }

                await Task.Delay(delay, token);
            }
        }
        catch (OperationCanceledException)
        {
        }
        finally
        {
            info.Completed = true;
        }
    }

    public sealed class RecordingInfo
    {
        public string Id { get; init; } = string.Empty;
        public DateTimeOffset StartedAt { get; init; }
        public int Fps { get; init; }
        public int? DurationSeconds { get; init; }
        public string File { get; init; } = string.Empty;
        public CancellationTokenSource? StopToken { get; set; }
        public bool Completed { get; set; }
    }
}

sealed class MetricsStore
{
    private readonly object _lock = new();
    private MetricsSnapshot _snapshot = MetricsSnapshot.Empty;
    private NetworkSample? _previousNetwork;
    private readonly PerformanceCounter _cpuCounter = new("Processor", "% Processor Time", "_Total");

    public MetricsSnapshot GetSnapshot()
    {
        lock (_lock)
        {
            return _snapshot;
        }
    }

    public void Sample()
    {
        var uptime = TimeSpan.FromMilliseconds(Environment.TickCount64);
        var cpuUsage = GetCpuUsage();
        var gpuUsage = GetGpuUsage();
        var memory = SystemMetrics.GetMemoryStatus();
        var temperatures = TemperatureReader.GetTemperatures();
        var battery = BatteryReader.GetBatteryStatus();
        var network = GetNetworkStats();
        var disks = DriveInfo.GetDrives()
            .Where(d => d.IsReady)
            .Select(d => new DiskInfo(
                d.Name,
                d.TotalSize,
                d.AvailableFreeSpace))
            .ToList();
        var processes = Process.GetProcesses()
            .OrderByDescending(p => p.WorkingSet64)
            .Take(8)
            .Select(p =>
            {
                var cpuSeconds = 0d;
                try
                {
                    cpuSeconds = p.TotalProcessorTime.TotalSeconds;
                }
                catch
                {
                    cpuSeconds = 0;
                }
                return new ProcessInfo(p.Id, p.ProcessName, p.WorkingSet64, cpuSeconds);
            })
            .ToList();

        var snapshot = new MetricsSnapshot(
            uptime.TotalSeconds,
            cpuUsage,
            gpuUsage,
            new MemoryInfo(memory.Total, memory.Available, memory.Used, memory.Percent),
            temperatures,
            battery,
            network,
            disks,
            processes);

        lock (_lock)
        {
            _snapshot = snapshot;
        }
    }

    private double GetCpuUsage()
    {
        try
        {
            _ = _cpuCounter.NextValue();
            Thread.Sleep(100);
            return Math.Round(_cpuCounter.NextValue(), 2);
        }
        catch
        {
            return 0;
        }
    }

    private double? GetGpuUsage()
    {
        try
        {
            var category = new PerformanceCounterCategory("GPU Engine");
            var counters = category.GetInstanceNames()
                .Where(name => name.Contains("engtype_3D", StringComparison.OrdinalIgnoreCase))
                .Select(name => new PerformanceCounter("GPU Engine", "Utilization Percentage", name))
                .ToList();

            if (counters.Count == 0)
            {
                return null;
            }

            double sum = 0;
            foreach (var counter in counters)
            {
                _ = counter.NextValue();
            }
            Thread.Sleep(100);
            foreach (var counter in counters)
            {
                sum += counter.NextValue();
            }
            return Math.Round(Math.Min(sum, 100), 2);
        }
        catch
        {
            return null;
        }
    }

    private NetworkInfo GetNetworkStats()
    {
        var now = DateTimeOffset.UtcNow;
        long received = 0;
        long sent = 0;

        foreach (var nic in NetworkInterface.GetAllNetworkInterfaces())
        {
            if (nic.OperationalStatus != OperationalStatus.Up)
            {
                continue;
            }

            var stats = nic.GetIPv4Statistics();
            received += stats.BytesReceived;
            sent += stats.BytesSent;
        }

        if (_previousNetwork is null)
        {
            _previousNetwork = new NetworkSample(now, received, sent);
            return new NetworkInfo(0, 0);
        }

        var duration = Math.Max(1, (now - _previousNetwork.Timestamp).TotalSeconds);
        var download = (received - _previousNetwork.BytesReceived) / duration;
        var upload = (sent - _previousNetwork.BytesSent) / duration;
        _previousNetwork = new NetworkSample(now, received, sent);
        return new NetworkInfo(Math.Max(0, download), Math.Max(0, upload));
    }

    private record NetworkSample(DateTimeOffset Timestamp, long BytesReceived, long BytesSent);
}

sealed class MetricsSamplingService(MetricsStore metrics) : BackgroundService
{
    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        while (!stoppingToken.IsCancellationRequested)
        {
            metrics.Sample();
            try
            {
                await Task.Delay(TimeSpan.FromSeconds(1), stoppingToken);
            }
            catch (TaskCanceledException)
            {
                return;
            }
        }
    }
}

record MetricsSnapshot(
    double UptimeSeconds,
    double CpuUsagePercent,
    double? GpuUsagePercent,
    MemoryInfo Memory,
    TemperatureInfo? Temperatures,
    BatteryInfo? Battery,
    NetworkInfo Network,
    List<DiskInfo> Disks,
    List<ProcessInfo> Processes)
{
    public static MetricsSnapshot Empty => new(
        0,
        0,
        null,
        new MemoryInfo(0, 0, 0, 0),
        null,
        null,
        new NetworkInfo(0, 0),
        new List<DiskInfo>(),
        new List<ProcessInfo>());
}

record MemoryInfo(long Total, long Available, long Used, double Percent);

record TemperatureInfo(double? CpuCelsius, double? GpuCelsius);

record BatteryInfo(bool IsPresent, int ChargePercent, bool IsCharging, int? SecondsRemaining);

record NetworkInfo(double DownloadBytesPerSec, double UploadBytesPerSec);

record DiskInfo(string Name, long TotalBytes, long FreeBytes);

record ProcessInfo(int Id, string Name, long MemoryBytes, double CpuSeconds);

static class TemperatureReader
{
    public static TemperatureInfo? GetTemperatures()
    {
        try
        {
            double? cpuTemp = null;
            using var searcher = new ManagementObjectSearcher(
                "root\\WMI",
                "SELECT CurrentTemperature FROM MSAcpi_ThermalZoneTemperature");
            foreach (var obj in searcher.Get())
            {
                if (obj["CurrentTemperature"] is uint temp)
                {
                    cpuTemp = Math.Round((temp / 10.0) - 273.15, 1);
                    break;
                }
            }

            return new TemperatureInfo(cpuTemp, null);
        }
        catch
        {
            return null;
        }
    }
}

static class BatteryReader
{
    public static BatteryInfo? GetBatteryStatus()
    {
        try
        {
            var status = System.Windows.Forms.SystemInformation.PowerStatus;
            var present = status.BatteryChargeStatus != System.Windows.Forms.BatteryChargeStatus.NoSystemBattery;
            var percent = (int)Math.Round(status.BatteryLifePercent * 100);
            var charging = status.PowerLineStatus == System.Windows.Forms.PowerLineStatus.Online;
            var seconds = status.BatteryLifeRemaining >= 0
                ? (int?)status.BatteryLifeRemaining
                : null;
            return new BatteryInfo(present, percent, charging, seconds);
        }
        catch
        {
            return null;
        }
    }
}
