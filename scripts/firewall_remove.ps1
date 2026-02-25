$ruleName = "PCRemoteServer8000"
Get-NetFirewallRule -DisplayName $ruleName -ErrorAction SilentlyContinue | Remove-NetFirewallRule
Write-Host "Removed firewall rule if it existed"
