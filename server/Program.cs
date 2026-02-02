using System.Collections.Concurrent;
using System.Diagnostics;
using System.Drawing;
using System.Drawing.Imaging;
using System.Net;
using System.Net.Sockets;
using System.Text.Json;
using Microsoft.AspNetCore.Http.HttpResults;
using Microsoft.AspNetCore.Mvc;
using System.Windows.Forms;

const string ApiTokenEnv = "PC_REMOTE_API_TOKEN";
const string DefaultApiToken = "change-me";
const int DiscoveryPort = 9999;
const string DiscoveryMessage = "PC_REMOTE_DISCOVERY";
const int DiscoveryResponsePort = 8000;
const string StreamBoundary = "frame";

var builder = WebApplication.CreateBuilder(args);
builder.Services.AddSingleton<SessionStore>();
builder.Services.AddSingleton<DiscoveryService>();

var app = builder.Build();

app.Lifetime.ApplicationStarted.Register(() =>
{
    var discovery = app.Services.GetRequiredService<DiscoveryService>();
    discovery.Start();
});

app.Use(async (context, next) =>
{
    if (context.Request.Path == "/auth" || context.Request.Path == "/health")
    {
        await next();
        return;
    }

    var token = ExtractToken(context.Request);
    if (token is null || token != GetConfiguredToken())
    {
        context.Response.StatusCode = StatusCodes.Status401Unauthorized;
        await context.Response.WriteAsJsonAsync(new { detail = "Invalid or missing API token." });
        return;
    }

    await next();
});

app.MapPost("/auth", ([FromBody] AuthRequest request) =>
{
    var isValid = request.Token == GetConfiguredToken();
    if (!isValid)
    {
        return Results.Unauthorized();
    }

    return Results.Ok(new { status = "ok" });
});

app.MapPost("/mouse/move", () => Results.StatusCode(StatusCodes.Status501NotImplemented));
app.MapPost("/mouse/click", () => Results.StatusCode(StatusCodes.Status501NotImplemented));
app.MapPost("/keyboard/press", () => Results.StatusCode(StatusCodes.Status501NotImplemented));
app.MapPost("/system/volume", () => Results.StatusCode(StatusCodes.Status501NotImplemented));
app.MapPost("/system/launch", () => Results.StatusCode(StatusCodes.Status501NotImplemented));

app.MapGet("/system/status", () =>
{
    var cpuPercent = TryGetCpuUsage();
    var memory = GC.GetGCMemoryInfo();
    return Results.Ok(new
    {
        cpu_percent = cpuPercent,
        memory = new
        {
            total = memory.TotalAvailableMemoryBytes,
            available = Math.Max(0, memory.TotalAvailableMemoryBytes - memory.HeapSizeBytes),
            used = memory.HeapSizeBytes,
            percent = memory.TotalAvailableMemoryBytes > 0
                ? Math.Round(memory.HeapSizeBytes * 100.0 / memory.TotalAvailableMemoryBytes, 1)
                : 0,
        },
    });
});

app.MapGet("/screen/screenshot", () =>
{
    var pngBytes = CaptureScreenPng();
    return Results.File(pngBytes, "image/png");
});

app.MapGet("/screen/stream", async (HttpContext context, int fps = 5) =>
{
    context.Response.ContentType = $"multipart/x-mixed-replace; boundary={StreamBoundary}";
    context.Response.Headers.CacheControl = "no-cache";
    var interval = TimeSpan.FromSeconds(1.0 / Math.Clamp(fps, 1, 30));

    while (!context.RequestAborted.IsCancellationRequested)
    {
        var pngBytes = CaptureScreenPng();
        await WriteMultipartFrame(context.Response, pngBytes, "image/png");
        await Task.Delay(interval, context.RequestAborted);
    }
});

app.MapGet("/camera/stream", () => Results.StatusCode(StatusCodes.Status501NotImplemented));
app.MapGet("/camera/photo", () => Results.StatusCode(StatusCodes.Status501NotImplemented));

app.MapPost("/screen/record/start", () => Results.StatusCode(StatusCodes.Status501NotImplemented));
app.MapPost("/screen/record/stop/{recordingId}", () => Results.StatusCode(StatusCodes.Status501NotImplemented));
app.MapGet("/screen/recordings", () => Results.Ok(new { recordings = new Dictionary<string, object>() }));

app.MapPost("/session/start", (SessionStartRequest request, SessionStore store) =>
{
    var session = store.Create(request.ClientName, request.TimeoutSeconds);
    return Results.Ok(session);
});

app.MapPost("/session/heartbeat", (SessionHeartbeatRequest request, SessionStore store) =>
{
    return store.Touch(request.SessionId) is { } session
        ? Results.Ok(session)
        : Results.NotFound(new { detail = "Session not found." });
});

app.MapPost("/session/end", (SessionEndRequest request, SessionStore store) =>
{
    return store.End(request.SessionId)
        ? Results.Ok(new { status = "ok" })
        : Results.NotFound(new { detail = "Session not found." });
});

app.MapGet("/session/status/{sessionId}", (string sessionId, SessionStore store) =>
{
    return store.Touch(sessionId) is { } session
        ? Results.Ok(session)
        : Results.NotFound(new { detail = "Session not found." });
});

app.MapPost("/system/power", () => Results.StatusCode(StatusCodes.Status501NotImplemented));

app.MapGet("/health", () => Results.Ok(new { status = "ok" }));

app.Run("http://0.0.0.0:8000");

static string GetConfiguredToken()
    => Environment.GetEnvironmentVariable(ApiTokenEnv) ?? DefaultApiToken;

static string? ExtractToken(HttpRequest request)
{
    var authHeader = request.Headers.Authorization.ToString();
    if (!string.IsNullOrWhiteSpace(authHeader) && authHeader.StartsWith("Bearer ", StringComparison.OrdinalIgnoreCase))
    {
        return authHeader["Bearer ".Length..].Trim();
    }

    return request.Headers["X-API-Token"].ToString();
}

static double TryGetCpuUsage()
{
    try
    {
        using var counter = new PerformanceCounter("Processor", "% Processor Time", "_Total");
        _ = counter.NextValue();
        Thread.Sleep(100);
        return Math.Round(counter.NextValue(), 1);
    }
    catch
    {
        return 0;
    }
}

static byte[] CaptureScreenPng()
{
    var screen = Screen.PrimaryScreen ?? throw new InvalidOperationException("No screen available.");
    var bounds = screen.Bounds;
    using var bitmap = new Bitmap(bounds.Width, bounds.Height);
    using var graphics = Graphics.FromImage(bitmap);
    graphics.CopyFromScreen(bounds.Left, bounds.Top, 0, 0, bounds.Size);
    using var stream = new MemoryStream();
    bitmap.Save(stream, ImageFormat.Png);
    return stream.ToArray();
}

static async Task WriteMultipartFrame(HttpResponse response, byte[] frameBytes, string contentType)
{
    var header = $"--{StreamBoundary}\r\nContent-Type: {contentType}\r\n\r\n";
    await response.WriteAsync(header);
    await response.Body.WriteAsync(frameBytes);
    await response.WriteAsync("\r\n");
    await response.Body.FlushAsync();
}

record AuthRequest(string Token);
record SessionStartRequest(string? ClientName, int TimeoutSeconds = 900);
record SessionHeartbeatRequest(string SessionId);
record SessionEndRequest(string SessionId);

class SessionStore
{
    private readonly ConcurrentDictionary<string, SessionInfo> _sessions = new();

    public SessionResponse Create(string? clientName, int timeoutSeconds)
    {
        var sessionId = Guid.NewGuid().ToString();
        var now = DateTimeOffset.UtcNow;
        var timeout = TimeSpan.FromSeconds(Math.Clamp(timeoutSeconds, 30, 86400));
        var info = new SessionInfo(clientName ?? "unknown", now, now, timeout);
        _sessions[sessionId] = info;
        return new SessionResponse(sessionId, now + timeout);
    }

    public SessionResponse? Touch(string sessionId)
    {
        if (!_sessions.TryGetValue(sessionId, out var info))
        {
            return null;
        }

        var now = DateTimeOffset.UtcNow;
        if (now - info.LastSeen > info.Timeout)
        {
            _sessions.TryRemove(sessionId, out _);
            return null;
        }

        var updated = info with { LastSeen = now };
        _sessions[sessionId] = updated;
        return new SessionResponse(sessionId, now + updated.Timeout);
    }

    public bool End(string sessionId)
        => _sessions.TryRemove(sessionId, out _);

    private record SessionInfo(string ClientName, DateTimeOffset CreatedAt, DateTimeOffset LastSeen, TimeSpan Timeout);
    public record SessionResponse(string SessionId, DateTimeOffset ExpiresAt);
}

class DiscoveryService
{
    private readonly ILogger<DiscoveryService> _logger;
    private CancellationTokenSource? _cts;

    public DiscoveryService(ILogger<DiscoveryService> logger)
    {
        _logger = logger;
    }

    public void Start()
    {
        if (_cts != null)
        {
            return;
        }

        _cts = new CancellationTokenSource();
        _ = Task.Run(() => ListenAsync(_cts.Token));
    }

    private async Task ListenAsync(CancellationToken token)
    {
        using var udp = new UdpClient(new IPEndPoint(IPAddress.Any, DiscoveryPort));
        _logger.LogInformation("Discovery listener started on UDP {Port}", DiscoveryPort);

        while (!token.IsCancellationRequested)
        {
            UdpReceiveResult result;
            try
            {
                result = await udp.ReceiveAsync(token);
            }
            catch (OperationCanceledException)
            {
                break;
            }

            var message = System.Text.Encoding.UTF8.GetString(result.Buffer).Trim();
            if (!string.Equals(message, DiscoveryMessage, StringComparison.Ordinal))
            {
                continue;
            }

            var payload = JsonSerializer.Serialize(new
            {
                port = DiscoveryResponsePort,
                token = GetConfiguredToken(),
                ips = GetHostIps(),
            });
            var bytes = System.Text.Encoding.UTF8.GetBytes(payload);
            await udp.SendAsync(bytes, bytes.Length, result.RemoteEndPoint);
        }
    }
}

static List<string> GetHostIps()
{
    var ips = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
    try
    {
        foreach (var address in Dns.GetHostAddresses(Dns.GetHostName()))
        {
            if (address.AddressFamily == AddressFamily.InterNetwork && !IPAddress.IsLoopback(address))
            {
                ips.Add(address.ToString());
            }
        }
    }
    catch
    {
        // ignored
    }

    return ips.Count > 0 ? ips.ToList() : new List<string> { "127.0.0.1" };
}
