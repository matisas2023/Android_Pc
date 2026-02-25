$ruleName = "PCRemoteServer8000"
$existing = Get-NetFirewallRule -DisplayName $ruleName -ErrorAction SilentlyContinue
if (-not $existing) {
    New-NetFirewallRule -DisplayName $ruleName -Direction Inbound -Action Allow -Protocol TCP -LocalPort 8000 | Out-Null
    Write-Host "Added firewall rule $ruleName"
} else {
    Write-Host "Rule already exists"
}
