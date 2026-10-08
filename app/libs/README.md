# Optional HEV Android AAR

No third-party binary is committed here.

For Stage-2, fetch the official HEV 2.18.0 AAR locally:

Linux/macOS:

    ./tools/fetch_hev_aar.sh

Windows PowerShell:

    .\tools\fetch_hev_aar.ps1

Expected file:

    app/libs/hev-socks5-tunnel.aar

Expected SHA-256:

    15ec8ed121663b562c99caa5bb602d1009f24e5b09e733438b81988f12feaaab

The app module includes this AAR only when the file exists. Stage-0/1 remains
buildable without it.
