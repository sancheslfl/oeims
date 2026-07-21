[CmdletBinding()]
param()

$parseErrors = foreach ($script in Get-ChildItem $PSScriptRoot -Filter "*.ps1") {
    $tokens = $null
    $errors = $null

    [System.Management.Automation.Language.Parser]::ParseFile(
        $script.FullName,
        [ref]$tokens,
        [ref]$errors
    ) | Out-Null

    $errors
}

if ($parseErrors) {
    $parseErrors | ForEach-Object {
        Write-Error "$($_.Extent.File):$($_.Extent.StartLineNumber): $($_.Message)"
    }

    throw "One or more PowerShell scripts contain syntax errors."
}

Write-Host "All PowerShell scripts parsed successfully." -ForegroundColor Green
