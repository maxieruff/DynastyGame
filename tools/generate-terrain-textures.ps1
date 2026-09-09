Add-Type -AssemblyName System.Drawing

$outputDirectory = Join-Path $PSScriptRoot '..\assets\textures'

function Write-Texture {
    param(
        [string] $Name,
        [string[]] $Pixels,
        [hashtable] $Palette
    )

    $bitmap = [System.Drawing.Bitmap]::new(16, 16, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    try {
        for ($y = 0; $y -lt 16; $y++) {
            for ($x = 0; $x -lt 16; $x++) {
                $key = [string]$Pixels[$y][$x]
                $bitmap.SetPixel($x, $y, [System.Drawing.ColorTranslator]::FromHtml($Palette[$key]))
            }
        }
        $bitmap.Save((Join-Path $outputDirectory $Name), [System.Drawing.Imaging.ImageFormat]::Png)
    }
    finally {
        $bitmap.Dispose()
    }
}

# Soft grass blades and small shadowed turf clumps. Edges deliberately match for seamless tiling.
Write-Texture 'grass.png' @(
    'GGGGGGGGGGGGGGGG', 'GGGHGGGGGGGGGGGG', 'GGGIHGGGGGGGGGGG', 'GGGGGGGGGGGGHGGG',
    'GGGGIGGGGGGGGGGG', 'GGGGGGGGHJGGGGGG', 'GGGGGGGGIGGGGGGG', 'GGGGGGIGGGGGHGGG',
    'GGGGGGGGGGGGHGGG', 'GGGHGGGGGGGGIGGG', 'GGGIHGGGGGGGGGGG', 'GGGGGGGGGGIGGGGG',
    'GGGGGGGGHJGGGGGG', 'GGGGIGGGGGGGGGGG', 'GGGGGGGGGGGGHGGG', 'GGGGGGGGGGGGGGGG'
) @{ G = '#4D9B3C'; H = '#35712D'; I = '#69B34A'; J = '#2D6028' }

# Rich, loamy soil with pebbles and tiny darker creases; no hard tile border.
Write-Texture 'dirt.png' @(
    'DDDDDDDDDDDDDDDD', 'DDDDDDDDDDDDDDDD', 'DDDDEDDDDDDDDDDD', 'DDDDDFDDDDDDDDDD',
    'DDDDDDDDDDDDDDDD', 'DDDDDDDDDFDDDDDD', 'DDDDEDDDDDDDDDDD', 'DDEDDDDDDDDDDDDD',
    'DDDFDDDDDDDDDDDD', 'DDDDDDDDDDDDDFDD', 'DDDDDDDDDDDDDDDD', 'DDDDDDDDDEDDDDDD',
    'DDDDDFDDDDDDDDDD', 'DDDDDDDDDDDDDDDD', 'DDEDDDDDDDDDDDDD', 'DDDDDDDDDDDDDDDD'
) @{ D = '#9A5B2E'; E = '#6E3E22'; F = '#BF7A42' }

# Cool stone slabs: quiet hairline cracks, irregular flecks, and restrained highlights.
Write-Texture 'stone.png' @(
    'SSSSSSSSSSSSSSSS', 'SSSSSSSSSSSSSSSS', 'SSSSSSSSSSSTSSSS', 'SSSSSSSSSSSTTSSS',
    'SSSSSSSSSSSSSSSS', 'SSSSUSSSSSSSSSSS', 'SSSSUUSSSSSSSSSS', 'SSSSSSSSSSSSSSSS',
    'SSSSSSSSSSSSSSSS', 'SSSSSSSSSSSSUSSS', 'SSSSSSSSSSSSUUSS', 'SSSSSSSTTSSSSSSS',
    'SSSSSSSSSSSSSSSS', 'SSSSSSSSSSSSSSSS', 'SSSSSSSSSSSTSSSS', 'SSSSSSSSSSSTTSSS'
) @{ S = '#7F8584'; T = '#56605F'; U = '#AAB1AD' }
