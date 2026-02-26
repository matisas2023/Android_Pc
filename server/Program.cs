using System.Collections.Concurrent;
using System.Diagnostics;
using System.Net;
using System.Net.NetworkInformation;
using System.Runtime.InteropServices;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json.Serialization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.RateLimiting;
using Serilog;
using System.Windows.Forms;

var builder = WebApplication.CreateBuilder(args);

if (string.IsNullOrWhiteSpace(Environment.GetEnvironmentVariable("ASPNETCORE_URLS")))
{
    builder.WebHost.UseUrls("http://0.0.0.0:8000", "http://[::]:8000");
}

builder.Host.UseSerilog((ctx, cfg) => cfg
    .MinimumLevel.Information()
    .WriteTo.Console()
    .WriteTo.File("logs/audit-.log", rollingInterval: RollingInterval.Day));

builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen();
builder.Services.ConfigureHttpJsonOptions(o =>
{
    o.SerializerOptions.DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull;
});

builder.Services.AddSingleton<SecurityState>();
builder.Services.AddSingleton<ReplayProtectionService>();
builder.Services.AddSingleton<IStatusService, StatusService>();
builder.Services.AddSingleton<ISystemService, SystemService>();
builder.Services.AddSingleton<IInputService, InputService>();
builder.Services.AddSingleton<IScreenService, ScreenService>();
builder.Services.AddSingleton<IMediaService, MediaService>();
builder.Services.AddSingleton<IProcessService, ProcessService>();
builder.Services.AddSingleton<IFileService, FileService>();
builder.Services.AddSingleton<IClipboardService, ClipboardService>();
builder.Services.AddHostedService<PairingCodeRotationService>();
builder.Services.AddHostedService<DiscoveryService>();

builder.Services.AddRateLimiter(options =>
{
    options.RejectionStatusCode = StatusCodes.Status429TooManyRequests;
    options.AddFixedWindowLimiter("default", lim =>
    {
        lim.Window = TimeSpan.FromSeconds(10);
        lim.PermitLimit = 60;
        lim.QueueLimit = 0;
    });
});

var app = builder.Build();

app.UseRateLimiter();
app.UseSwagger();
app.UseSwaggerUI();

app.Use(async (ctx, next) =>
{
    if (ctx.Request.Path.StartsWithSegments("/swagger")
        || ctx.Request.Path.StartsWithSegments("/openapi")
        || ctx.Request.Path == "/"
        || ctx.Request.Path == "/api/v1/pairing/code"
        || ctx.Request.Path == "/api/v1/pairing/pair"
        || ctx.Request.Path == "/health")
    {
        await next();
        return;
    }

    var sec = ctx.RequestServices.GetRequiredService<SecurityState>();
    var auth = ctx.Request.Headers.Authorization.ToString();
    if (!auth.StartsWith("Bearer ", StringComparison.OrdinalIgnoreCase))
    {
        ctx.Response.StatusCode = StatusCodes.Status401Unauthorized;
        await ctx.Response.WriteAsJsonAsync(new { detail = "Missing bearer token" });
        return;
    }

    var token = auth.Substring("Bearer ".Length).Trim();
    if (!sec.IsValidToken(token))
    {
        ctx.Response.StatusCode = StatusCodes.Status401Unauthorized;
        await ctx.Response.WriteAsJsonAsync(new { detail = "Invalid token" });
        return;
    }

    if (ctx.Request.Method is "POST" or "PUT" or "DELETE")
    {
        var replay = ctx.RequestServices.GetRequiredService<ReplayProtectionService>();
        var ts = ctx.Request.Headers["X-Timestamp"].ToString();
        var nonce = ctx.Request.Headers["X-Nonce"].ToString();
        if (!replay.Validate(token, ts, nonce, out var reason))
        {
            ctx.Response.StatusCode = StatusCodes.Status400BadRequest;
            await ctx.Response.WriteAsJsonAsync(new { detail = reason });
            return;
        }
    }

    await next();
});

app.MapGet("/", () => Results.Ok(new { name = "RemoteControl.Server", version = "v2" }));
app.MapGet("/health", () => Results.Ok(new { status = "ok" }));

app.MapGet("/api/v1/pairing/code", ([FromServices] SecurityState sec, HttpContext ctx) =>
{
    if (!IsLocalRequest(ctx.Connection.RemoteIpAddress))
    {
        return Results.StatusCode(StatusCodes.Status403Forbidden);
    }

    var code = sec.GetCurrentPairingCode();
    return Results.Ok(new { code.Value, code.ExpiresAtUtc });
});

app.MapPost("/api/v1/pairing/pair", ([FromBody] PairingRequest req, [FromServices] SecurityState sec) =>
{
    if (!sec.TryPair(req.Code, req.ClientName ?? "android", out var token))
    {
        return Results.Unauthorized();
    }

    return Results.Ok(new PairingResponse(token!, DateTimeOffset.UtcNow.AddDays(30)));
});

app.MapGet("/api/v1/status", ([FromServices] IStatusService status) => Results.Ok(status.GetStatus()))
    .RequireRateLimiting("default");

app.MapPost("/api/v1/system/power", ([FromBody] PowerRequest req, [FromServices] ISystemService system, HttpContext ctx) =>
{
    var ok = system.Power(req.Action);
    Audit(ctx, "system.power", req.Action, ok ? "ok" : "fail");
    return ok ? Results.Ok(new { status = "ok" }) : Results.BadRequest(new { detail = "unsupported action" });
});

app.MapGet("/api/v1/screen/screenshot", ([FromServices] IScreenService screen) =>
{
    var bytes = screen.CapturePng();
    return Results.Bytes(bytes, "image/png");
});

app.MapGet("/api/v1/camera/photo", ([FromServices] IScreenService screen) =>
{
    var bytes = screen.CaptureCameraJpeg();
    return bytes == null ? Results.Problem("Camera not available", statusCode: 503) : Results.Bytes(bytes, "image/jpeg");
});

app.MapGet("/api/v1/processes", ([FromServices] IProcessService svc) => Results.Ok(svc.List()));
app.MapPost("/api/v1/processes/{pid:int}/kill", (int pid, [FromQuery] bool confirm, [FromServices] IProcessService svc) =>
{
    if (!confirm) return Results.BadRequest(new { detail = "confirm=true required" });
    return svc.Kill(pid) ? Results.Ok(new { status = "ok" }) : Results.NotFound();
});

app.MapGet("/api/v1/files/list", ([FromQuery] string path, [FromServices] IFileService files) => Results.Ok(files.List(path)));
app.MapPost("/api/v1/files/folder", ([FromBody] CreateFolderRequest req, [FromServices] IFileService files) => Results.Ok(files.CreateFolder(req.Path)));
app.MapPost("/api/v1/files/rename", ([FromBody] RenameRequest req, [FromServices] IFileService files) => Results.Ok(files.Rename(req.Source, req.Target)));
app.MapDelete("/api/v1/files", ([FromQuery] string path, [FromServices] IFileService files) => Results.Ok(files.Delete(path)));
app.MapGet("/api/v1/files/download", ([FromQuery] string path) => Results.File(path, "application/octet-stream", Path.GetFileName(path)));
app.MapPost("/api/v1/files/upload", async ([FromQuery] string targetDir, HttpRequest req, [FromServices] IFileService files) =>
{
    var form = await req.ReadFormAsync();
    var file = form.Files.FirstOrDefault();
    if (file == null) return Results.BadRequest(new { detail = "file missing" });
    var saved = await files.Upload(targetDir, file);
    return Results.Ok(new { saved });
});

app.MapGet("/api/v1/clipboard", ([FromServices] IClipboardService cb) => Results.Ok(new { text = cb.ReadText() }));
app.MapPost("/api/v1/clipboard", ([FromBody] ClipboardWriteRequest req, [FromServices] IClipboardService cb) =>
{
    cb.WriteText(req.Text);
    return Results.Ok(new { status = "ok" });
});

app.MapPost("/api/v1/media", ([FromBody] MediaRequest req, [FromServices] IMediaService media) =>
{
    var ok = media.Execute(req.Action);
    return ok ? Results.Ok(new { status = "ok" }) : Results.BadRequest();
});

app.MapPost("/api/v1/input/mouse/move", ([FromBody] MouseMoveRequest req, [FromServices] IInputService input) => { input.MoveMouse(req.Dx, req.Dy); return Results.Ok(); });
app.MapPost("/api/v1/input/mouse/click", ([FromBody] MouseClickRequest req, [FromServices] IInputService input) => { input.Click(req.Button, req.Double); return Results.Ok(); });
app.MapPost("/api/v1/input/mouse/scroll", ([FromBody] MouseScrollRequest req, [FromServices] IInputService input) => { input.Scroll(req.Delta); return Results.Ok(); });
app.MapPost("/api/v1/input/keyboard/text", ([FromBody] KeyboardTextRequest req, [FromServices] IInputService input) => { input.TypeText(req.Text); return Results.Ok(); });
app.MapPost("/api/v1/input/keyboard/hotkey", ([FromBody] HotkeyRequest req, [FromServices] IInputService input) => { input.Hotkey(req.Keys); return Results.Ok(); });

app.Run();

static bool IsLocalRequest(IPAddress? ip)
{
    if (ip == null) return false;
    return IPAddress.IsLoopback(ip) || ip.ToString().StartsWith("192.168.") || ip.ToString().StartsWith("10.") || ip.ToString().StartsWith("172.");
}

static void Audit(HttpContext ctx, string command, string summary, string result)
{
    Log.Information("audit command={Command} summary={Summary} result={Result} client={Client}", command, summary, result, ctx.Connection.RemoteIpAddress?.ToString());
}

public record PairingCode(string Value, DateTimeOffset ExpiresAtUtc);
record PairingRequest(string Code, string? ClientName);
record PairingResponse(string Token, DateTimeOffset ExpiresAtUtc);
record PowerRequest(string Action);
record ClipboardWriteRequest(string Text);
record MediaRequest(string Action);
record MouseMoveRequest(int Dx, int Dy);
record MouseClickRequest(string Button = "left", bool Double = false);
record MouseScrollRequest(int Delta);
record KeyboardTextRequest(string Text);
record HotkeyRequest(List<string> Keys);
record CreateFolderRequest(string Path);
record RenameRequest(string Source, string Target);
record ProcessInfoDto(int Pid, string Name, long MemoryBytes);
record FileEntryDto(string Name, string FullPath, bool IsDirectory, long Size);

public sealed class SecurityState
{
    private readonly object _lock = new();
    private PairingCode _current = new("000000", DateTimeOffset.UtcNow);
    private readonly ConcurrentDictionary<string, DateTimeOffset> _tokens = new();

    public PairingCode GetCurrentPairingCode() { lock (_lock) { return _current; } }
    public void RotateCode()
    {
        lock (_lock)
        {
            _current = new PairingCode(RandomNumberGenerator.GetInt32(100000, 999999).ToString(), DateTimeOffset.UtcNow.AddMinutes(5));
            Console.WriteLine($"Pairing code: {_current.Value} (exp: {_current.ExpiresAtUtc:O})");
        }
    }

    public bool TryPair(string code, string clientName, out string? token)
    {
        token = null;
        lock (_lock)
        {
            if (!string.Equals(_current.Value, code, StringComparison.Ordinal) || _current.ExpiresAtUtc < DateTimeOffset.UtcNow)
                return false;
        }

        token = Convert.ToHexString(RandomNumberGenerator.GetBytes(32));
        _tokens[token] = DateTimeOffset.UtcNow.AddDays(30);
        Log.Information("paired client={ClientName}", clientName);
        return true;
    }

    public bool IsValidToken(string token)
    {
        if (!_tokens.TryGetValue(token, out var exp)) return false;
        return exp > DateTimeOffset.UtcNow;
    }
}

sealed class PairingCodeRotationService(SecurityState state) : BackgroundService
{
    public bool Power(string action)
    {
        state.RotateCode();
        while (!stoppingToken.IsCancellationRequested)
        {
            await Task.Delay(TimeSpan.FromMinutes(1), stoppingToken);
            var current = state.GetCurrentPairingCode();
            if (current.ExpiresAtUtc <= DateTimeOffset.UtcNow)
                state.RotateCode();
        }
        catch { return false; }
    }

    [DllImport("user32.dll")] static extern bool LockWorkStation();
}

sealed class DiscoveryService(ILogger<DiscoveryService> logger) : BackgroundService
{
    private const int DiscoveryPort = 9999;
    private const string DiscoveryMessage = "PC_REMOTE_DISCOVERY";

    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        using var udp = new UdpClient(DiscoveryPort);
        udp.EnableBroadcast = true;
        logger.LogInformation("Discovery listener started on UDP {Port}", DiscoveryPort);

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

            var message = Encoding.UTF8.GetString(result.Buffer).Trim();
            if (!string.Equals(message, DiscoveryMessage, StringComparison.Ordinal))
            {
                continue;
            }

            var payload = System.Text.Json.JsonSerializer.Serialize(new
            {
                port = ResolveServerPort(),
                ips = GetLocalIps(),
            });
            var bytes = Encoding.UTF8.GetBytes(payload);
            await udp.SendAsync(bytes, bytes.Length, result.RemoteEndPoint);
        }
    }

    private static int ResolveServerPort()
    {
        var raw = Environment.GetEnvironmentVariable("ASPNETCORE_URLS");
        if (string.IsNullOrWhiteSpace(raw)) return 8000;

        var first = raw.Split(';', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries).FirstOrDefault();
        if (first == null) return 8000;
        return Uri.TryCreate(first, UriKind.Absolute, out var uri) ? uri.Port : 8000;
    }

    private static List<string> GetLocalIps()
    {
        return NetworkInterface.GetAllNetworkInterfaces()
            .Where(n => n.OperationalStatus == OperationalStatus.Up && !n.IsReceiveOnly)
            .SelectMany(n => n.GetIPProperties().UnicastAddresses)
            .Where(a => a.Address.AddressFamily == System.Net.Sockets.AddressFamily.InterNetwork && !IPAddress.IsLoopback(a.Address))
            .Select(a => a.Address.ToString())
            .Distinct()
            .ToList();
    }
}

public sealed class ReplayProtectionService
{
    private readonly ConcurrentDictionary<string, DateTimeOffset> _nonces = new();

    public bool Validate(string token, string tsRaw, string nonce, out string reason)
    {
        reason = "ok";
        if (string.IsNullOrWhiteSpace(tsRaw) || string.IsNullOrWhiteSpace(nonce))
        {
            reason = "timestamp/nonce required";
            return false;
        }
        if (!long.TryParse(tsRaw, out var ts))
        {
            reason = "invalid timestamp";
            return false;
        }

        var now = DateTimeOffset.UtcNow.ToUnixTimeSeconds();
        if (Math.Abs(now - ts) > 60)
        {
            reason = "timestamp outside allowed window";
            return false;
        }

        var key = $"{token}:{nonce}";
        if (!_nonces.TryAdd(key, DateTimeOffset.UtcNow.AddMinutes(5)))
        {
            reason = "replay nonce";
            return false;
        }

        foreach (var item in _nonces.Where(x => x.Value < DateTimeOffset.UtcNow).ToList())
            _nonces.TryRemove(item.Key, out _);

        return true;
    }
}

interface IStatusService { object GetStatus(); }
sealed class StatusService : IStatusService
{
    public object GetStatus()
    {
        var uptime = TimeSpan.FromMilliseconds(Environment.TickCount64).TotalSeconds;
        var drives = DriveInfo.GetDrives().Where(d => d.IsReady).Select(d => new { d.Name, d.TotalSize, d.AvailableFreeSpace });
        var ips = NetworkInterface.GetAllNetworkInterfaces()
            .Where(n => n.OperationalStatus == OperationalStatus.Up)
            .SelectMany(n => n.GetIPProperties().UnicastAddresses)
            .Where(a => a.Address.AddressFamily == System.Net.Sockets.AddressFamily.InterNetwork)
            .Select(a => a.Address.ToString())
            .Distinct()
            .ToList();

        return new
        {
            uptimeSeconds = uptime,
            machineName = Environment.MachineName,
            userName = Environment.UserName,
            memory = new Microsoft.VisualBasic.Devices.ComputerInfo().TotalPhysicalMemory,
            drives,
            ips,
        };
    }
}

interface ISystemService { bool Power(string action); }
sealed class SystemService : ISystemService
{
    public bool Power(string action)
    {
        try
        {
            switch (action.ToLowerInvariant())
            {
                case "shutdown": Process.Start("shutdown", "/s /t 0"); return true;
                case "restart": Process.Start("shutdown", "/r /t 0"); return true;
                case "sleep": Process.Start("rundll32.exe", "powrprof.dll,SetSuspendState 0,1,0"); return true;
                case "lock": LockWorkStation(); return true;
                default: return false;
            }
        }
        catch { return false; }
    }

    [DllImport("user32.dll")] static extern bool LockWorkStation();
}

interface IInputService
{
    void MoveMouse(int dx, int dy);
    void Click(string button, bool dbl);
    void Scroll(int delta);
    void TypeText(string text);
    void Hotkey(List<string> keys);
}
sealed class InputService : IInputService
{
    public void MoveMouse(int dx, int dy)
    {
        mouse_event(0x0001, dx, dy, 0, UIntPtr.Zero);
    }
    public void Click(string button, bool dbl)
    {
        var (down, up) = button.ToLowerInvariant() switch
        {
            "right" => (0x0008u, 0x0010u),
            _ => (0x0002u, 0x0004u),
        };
        mouse_event(down, 0, 0, 0, UIntPtr.Zero);
        mouse_event(up, 0, 0, 0, UIntPtr.Zero);
        if (dbl)
        {
            mouse_event(down, 0, 0, 0, UIntPtr.Zero);
            mouse_event(up, 0, 0, 0, UIntPtr.Zero);
        }
    }
    public void Scroll(int delta) => mouse_event(0x0800, 0, 0, (uint)delta, UIntPtr.Zero);
    public void TypeText(string text) => SendKeys.SendWait(text);
    public void Hotkey(List<string> keys)
    {
        var map = new Dictionary<string, byte> { ["ctrl"] = 0x11, ["alt"] = 0x12, ["shift"] = 0x10, ["win"] = 0x5B, ["esc"] = 0x1B, ["enter"] = 0x0D };
        var vks = keys.Select(k => map.GetValueOrDefault(k.ToLowerInvariant(), (byte)0)).Where(v => v != 0).ToList();
        foreach (var vk in vks) keybd_event(vk, 0, 0, UIntPtr.Zero);
        for (var i = vks.Count - 1; i >= 0; i--) keybd_event(vks[i], 0, 2, UIntPtr.Zero);
    }

    [DllImport("user32.dll")] static extern void mouse_event(uint dwFlags, int dx, int dy, uint dwData, UIntPtr dwExtraInfo);
    [DllImport("user32.dll")] static extern void keybd_event(byte bVk, byte bScan, uint dwFlags, UIntPtr dwExtraInfo);
}

interface IScreenService { byte[] CapturePng(); byte[]? CaptureCameraJpeg(); }
sealed class ScreenService : IScreenService
{
    public byte[] CapturePng()
    {
        var bounds = Screen.PrimaryScreen?.Bounds ?? new System.Drawing.Rectangle(0, 0, 1920, 1080);
        using var bmp = new System.Drawing.Bitmap(bounds.Width, bounds.Height);
        using var g = System.Drawing.Graphics.FromImage(bmp);
        g.CopyFromScreen(bounds.Location, System.Drawing.Point.Empty, bounds.Size);
        using var ms = new MemoryStream();
        bmp.Save(ms, System.Drawing.Imaging.ImageFormat.Png);
        return ms.ToArray();
    }
}

interface IClipboardService { string? ReadText(); void WriteText(string text); }
sealed class ClipboardService : IClipboardService
{
    public string? ReadText() => RunSta(() => Clipboard.ContainsText() ? Clipboard.GetText() : null);
    public void WriteText(string text) => RunSta(() => Clipboard.SetText(text));

    public byte[]? CaptureCameraJpeg()
    {
        // Minimal fallback to keep endpoint stable in rewrite phase.
        // If dedicated camera driver/library is unavailable, return null.
        return null;
    }
}

interface IMediaService { bool Execute(string action); }
sealed class MediaService : IMediaService
{
    public bool Execute(string action)
    {
        var vk = action.ToLowerInvariant() switch
        {
            "playpause" => 0xB3,
            "next" => 0xB0,
            "prev" => 0xB1,
            "volumeup" => 0xAF,
            "volumedown" => 0xAE,
            "mute" => 0xAD,
            _ => -1
        };
        if (vk < 0) return false;
        keybd_event((byte)vk, 0, 0, UIntPtr.Zero);
        keybd_event((byte)vk, 0, 2, UIntPtr.Zero);
        return true;
    }

    [DllImport("user32.dll")] static extern void keybd_event(byte bVk, byte bScan, uint dwFlags, UIntPtr dwExtraInfo);
}

interface IProcessService { List<ProcessInfoDto> List(); bool Kill(int pid); }
sealed class ProcessService : IProcessService
{
    public List<ProcessInfoDto> List() => Process.GetProcesses().Select(p =>
    {
        long mem = 0;
        try { mem = p.WorkingSet64; } catch { }
        return new ProcessInfoDto(p.Id, p.ProcessName, mem);
    }).OrderByDescending(x => x.MemoryBytes).Take(200).ToList();

    public bool Kill(int pid)
    {
        try { Process.GetProcessById(pid).Kill(); return true; } catch { return false; }
    }
}

interface IFileService
{
    List<FileEntryDto> List(string path);
    bool CreateFolder(string path);
    bool Rename(string src, string dst);
    bool Delete(string path);
    Task<string> Upload(string targetDir, IFormFile file);
}

sealed class FileService : IFileService
{
    public List<FileEntryDto> List(string path)
    {
        if (string.IsNullOrWhiteSpace(path)) path = Environment.GetFolderPath(Environment.SpecialFolder.UserProfile);
        var dir = new DirectoryInfo(path);
        if (!dir.Exists) return [];

        return dir.EnumerateFileSystemInfos().Select(f =>
        {
            var isDir = (f.Attributes & FileAttributes.Directory) != 0;
            var size = isDir ? 0 : (f as FileInfo)?.Length ?? 0;
            return new FileEntryDto(f.Name, f.FullName, isDir, size);
        }).ToList();
    }

    public bool CreateFolder(string path) { try { Directory.CreateDirectory(path); return true; } catch { return false; } }
    public bool Rename(string src, string dst)
    {
        try
        {
            if (File.Exists(src)) File.Move(src, dst, true);
            else if (Directory.Exists(src)) Directory.Move(src, dst);
            else return false;
            return true;
        }
        catch { return false; }
    }
    public bool Delete(string path)
    {
        try
        {
            if (File.Exists(path)) File.Delete(path);
            else if (Directory.Exists(path)) Directory.Delete(path, true);
            else return false;
            return true;
        }
        catch { return false; }
    }

    public async Task<string> Upload(string targetDir, IFormFile file)
    {
        Directory.CreateDirectory(targetDir);
        var full = Path.Combine(targetDir, Path.GetFileName(file.FileName));
        await using var fs = File.Create(full);
        await file.CopyToAsync(fs);
        return full;
    }
}

interface IClipboardService { string? ReadText(); void WriteText(string text); }
sealed class ClipboardService : IClipboardService
{
    public string? ReadText() => RunSta(() => Clipboard.ContainsText() ? Clipboard.GetText() : null);
    public void WriteText(string text) => RunSta(() => Clipboard.SetText(text));

    private static T? RunSta<T>(Func<T?> action)
    {
        T? result = default;
        Exception? ex = null;
        var thread = new Thread(() =>
        {
            try { result = action(); }
            catch (Exception e) { ex = e; }
        });
        thread.SetApartmentState(ApartmentState.STA);
        thread.Start();
        thread.Join();
        if (ex != null) throw ex;
        return result;
    }

    private static void RunSta(Action action)
    {
        Exception? ex = null;
        var thread = new Thread(() =>
        {
            try { action(); }
            catch (Exception e) { ex = e; }
        });
        thread.SetApartmentState(ApartmentState.STA);
        thread.Start();
        thread.Join();
        if (ex != null) throw ex;
    }
}
