# Generates one cryptographically secure 32-byte secret, base64 encoded -
# the format JWT_SECRET and PLATFORM_ADMIN_JWT_SECRET both expect.
#
#   .\scripts\new-secret.ps1          one secret
#   .\scripts\new-secret.ps1 -Count 2 two secrets (they will differ)
#
# Why this exists: the documentation says `openssl rand -base64 32`, and
# openssl IS present on this machine - Git for Windows bundles it at
# /mingw64/bin/openssl - but it is NOT on PowerShell's PATH, so the documented
# command fails there with "openssl is not recognized". This is the PowerShell
# equivalent, using the same OS cryptographic RNG.
#
# Deliberately NOT Get-Random: that is a general-purpose PRNG seeded from a
# predictable source. It is fine for picking a test fixture and completely
# unfit for a signing key, which is what these secrets are - anyone able to
# predict one can forge a session for any user.

param(
    [ValidateRange(1, 20)]
    [int]$Count = 1
)

for ($i = 0; $i -lt $Count; $i++) {
    $bytes = New-Object byte[] 32
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $rng.GetBytes($bytes)
        [Convert]::ToBase64String($bytes)
    }
    finally {
        $rng.Dispose()
    }
}
