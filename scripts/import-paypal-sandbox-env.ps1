$ErrorActionPreference = 'Stop'
$envFile = Join-Path (Split-Path -Parent $PSScriptRoot) '.env.paypal.local'
$allowedNames = @('PAYPAL_CLIENT_ID', 'PAYPAL_CLIENT_SECRET', 'PAYPAL_WEBHOOK_ID')

if (-not (Test-Path -LiteralPath $envFile -PathType Leaf)) {
    throw 'Create the ignored root .env.paypal.local file with PayPal sandbox credentials first.'
}

$loaded = @{}
foreach ($line in Get-Content -LiteralPath $envFile) {
    if ($line -match '^\s*(PAYPAL_CLIENT_ID|PAYPAL_CLIENT_SECRET|PAYPAL_WEBHOOK_ID)\s*=\s*(.*?)\s*$') {
        $name = $Matches[1]
        $value = $Matches[2]
        if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or
                ($value.StartsWith("'") -and $value.EndsWith("'")))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        if ($value) {
            [Environment]::SetEnvironmentVariable($name, $value, 'Process')
            $loaded[$name] = $true
        }
    }
}

if (-not $loaded.ContainsKey('PAYPAL_CLIENT_ID') -or -not $loaded.ContainsKey('PAYPAL_CLIENT_SECRET')) {
    throw 'The local file must define PAYPAL_CLIENT_ID and PAYPAL_CLIENT_SECRET.'
}

Write-Output 'Loaded PayPal sandbox settings into this PowerShell process.'
