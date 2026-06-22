# rpi4 — Raspberry Pi Setup State

## Device Info

| Property | Value |
|---|---|
| Tailscale name | `rpi4` |
| Hostname (shell) | `ssm` |
| User | `mete` |
| Tailscale IP | `100.91.250.109` |
| OS | Linux (Raspberry Pi) |

## Tailscale

- Connected to Tailscale account: `metesaurabh@`
- Installed via: `curl -fsSL https://tailscale.com/install.sh | sh`
- Authenticated and online

### Other devices on the network

| Name | IP | Notes |
|---|---|---|
| `raspberrypi` | `100.123.110.92` | Existing RPi — runs WorDe Telegram bot |
| `mumbai-ec2` | `100.89.143.55` | Old EC2 where flight radar was deployed (OpenSky blocked it) |
| `saurabhs-macbook-air` | `100.124.168.95` | Dev machine |

## Tailscale Funnel

- Public URL: `https://rpi4.taile11f51.ts.net/`
- Proxies to: `http://127.0.0.1:8000`
- To start persistently: `sudo tailscale funnel --bg 8000`
- To start temporarily (foreground): `sudo tailscale funnel 8000`

## Why RPi instead of EC2

OpenSky Network blocks cloud provider IPs (AWS, GCP, Azure). The RPi at home uses a residential ISP IP which OpenSky allows. EC2 was the previous deployment target but flight data requests were blocked.

## Flight Radar App

- **Not yet deployed** on this RPi
- Previously ran on EC2 at `https://<public-ip>:8000`
- App serves a website showing planes flying overhead
- Backend fetches data from OpenSky Network API
- Target port: `8000`
- Once deployed, public access via Tailscale Funnel: `https://rpi4.taile11f51.ts.net/`

## Next Steps

1. Clone flight radar repo onto `rpi4`
2. Install dependencies
3. Run app on port 8000
4. Enable persistent Tailscale Funnel: `sudo tailscale funnel --bg 8000`
5. Set up systemd service so app auto-starts on reboot
