using Xunit;

public class SecurityTests
{
    [Fact]
    public void Pairing_Code_Should_Issue_Token()
    {
        var state = new SecurityState();
        state.RotateCode();
        var code = state.GetCurrentPairingCode().Value;

        var ok = state.TryPair(code, "test-client", out var token);

        Assert.True(ok);
        Assert.False(string.IsNullOrWhiteSpace(token));
    }

    [Fact]
    public void Issued_Token_Should_Be_Valid_For_Authorization_Check()
    {
        var state = new SecurityState();
        state.RotateCode();
        var code = state.GetCurrentPairingCode().Value;

        state.TryPair(code, "test-client", out var token);

        Assert.NotNull(token);
        Assert.True(state.IsValidToken(token!));
        Assert.False(state.IsValidToken("bad-token"));
    }

    [Fact]
    public void Replay_Protection_Should_Reject_Same_Nonce()
    {
        var replay = new ReplayProtectionService();
        var ts = DateTimeOffset.UtcNow.ToUnixTimeSeconds().ToString();

        var first = replay.Validate("tkn", ts, "nonce-1", out _);
        var second = replay.Validate("tkn", ts, "nonce-1", out var reason);

        Assert.True(first);
        Assert.False(second);
        Assert.Equal("replay nonce", reason);
    }
}
