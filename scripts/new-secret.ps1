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

# Regenerates until the encoding contains no '+' or '/'. Both are perfectly
# legal base64 and the application decodes them fine - but they are the
# characters that get mangled between here and a server: escaped by a shell,
# turned into '\/' by something JSON-ish, or line-wrapped by a paste. A
# production deploy on 2026-09-05 died on
#
#     DecodingException: Illegal base64 character: '\'
#
# because a secret arrived with a backslash in it. Emitting only letters,
# digits and the trailing '=' padding removes that whole class of failure at
# no cost to entropy - the bytes are still 32 from the OS CSPRNG, and roughly
# one candidate in four is clean, so this loop is cheap.
for ($i = 0; $i -lt $Count; $i++) {
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $attempts = 0
        do {
            $bytes = New-Object byte[] 32
            $rng.GetBytes($bytes)
            $secret = [Convert]::ToBase64String($bytes)
            $attempts++
        } while (($secret -match '[+/]') -and ($attempts -lt 200))

        if ($secret -match '[+/]') {
            # Astronomically unlikely; emit it anyway rather than fail. It is a
            # valid secret - it just needs care when pasting.
            Write-Warning "Could not produce a '+'/'/'-free value in $attempts attempts; the value below is still valid base64, paste it exactly."
        }
        $secret
    }
    finally {
        $rng.Dispose()
    }
}
