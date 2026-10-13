[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidateNotNullOrEmpty()][string]$Issuer,
    [Parameter(Mandatory)][ValidateNotNullOrEmpty()][string]$Subject,
    [string]$ContainerName,
    [string]$DatabaseUser = 'suitefolk',
    [string]$DatabaseName = 'suitefolk',
    [switch]$Disable
)

$ErrorActionPreference = 'Stop'
$issuerUri = $null
if ($Issuer.Length -gt 512 -or -not [Uri]::TryCreate($Issuer, [UriKind]::Absolute, [ref]$issuerUri) -or
    $issuerUri.Scheme -ne 'https' -or $issuerUri.UserInfo -or $issuerUri.Query -or $issuerUri.Fragment -or
    -not $Issuer.EndsWith('/')) {
    throw 'Issuer must be an HTTPS issuer URL ending in a slash, without credentials, query, or fragment.'
}
if ($Subject.Length -gt 255 -or [string]::IsNullOrWhiteSpace($Subject)) {
    throw 'Subject must be the Auth0 User ID, with at most 255 characters.'
}
if (-not $ContainerName) {
    $repoRoot = Split-Path -Parent $PSScriptRoot
    $ContainerName = & docker compose --project-directory $repoRoot -f (Join-Path $repoRoot 'compose.yaml') ps -q postgres
    if ($LASTEXITCODE -ne 0 -or -not $ContainerName) { throw 'Start the local PostgreSQL service first.' }
}
if (@($ContainerName).Count -ne 1 -or $ContainerName.StartsWith('-')) { throw 'Specify exactly one PostgreSQL container.' }

# psql quotes each supplied value as a SQL literal; repeat provisioning preserves the UUID.
$sql = @'
INSERT INTO owner_access (issuer, subject, enabled)
VALUES (:'owner_issuer', :'owner_subject', :'owner_enabled'::boolean)
ON CONFLICT (issuer, subject) DO UPDATE SET enabled = EXCLUDED.enabled
RETURNING id, enabled;
'@
$enabled = if ($Disable) { 'false' } else { 'true' }
$dockerArguments = @('exec', '-i', $ContainerName, 'psql', '-X', '-v', 'ON_ERROR_STOP=1',
    '-U', $DatabaseUser, '-d', $DatabaseName,
    '-v', "owner_issuer=$Issuer", '-v', "owner_subject=$Subject", '-v', "owner_enabled=$enabled")
$sql | & docker @dockerArguments
if ($LASTEXITCODE -ne 0) { throw 'Owner provisioning failed; check the database and applied migrations.' }
