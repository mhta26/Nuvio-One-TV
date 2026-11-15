#!/usr/bin/env python3
"""Fill missing official client configuration from Nuvio's public discovery document."""
import argparse
import base64
import json
from pathlib import Path
import urllib.request

OFFICIAL_BACKEND = 'https://api.nuvio.tv'
DISCOVERY_URL = OFFICIAL_BACKEND + '/.well-known/nuvio'
URL_KEY = 'NUVIO_SUPABASE_URL'
PUBLIC_KEY = 'NUVIO_SUPABASE_ANON_KEY'


def complete_backend(properties, document):
    result = dict(properties)
    current_url = result.get(URL_KEY, '').strip().rstrip('/')
    if current_url and result.get(PUBLIC_KEY, '').strip():
        return result
    if current_url and current_url != OFFICIAL_BACKEND:
        raise ValueError('Custom backend configuration needs its own publishable key')
    if document.get('version') != 1 or document.get('service') != 'nuvio':
        raise ValueError('Unexpected Nuvio discovery document')
    if document.get('backend_url', '').rstrip('/') != OFFICIAL_BACKEND:
        raise ValueError('Official discovery returned an unexpected backend')
    if not document.get('capabilities', {}).get('tv_login'):
        raise ValueError('The backend does not advertise TV login')
    key = document.get('publishable_key', '').strip()
    if not key or '\n' in key or '\r' in key:
        raise ValueError('No valid public client key was advertised')
    if key.count('.') == 2:
        payload = key.split('.')[1]
        claims = json.loads(base64.urlsafe_b64decode(payload + '=' * (-len(payload) % 4)))
        if claims.get('role') != 'anon':
            raise ValueError('Only an anonymous public client key is accepted')
    elif not key.startswith('sb_publishable_'):
        raise ValueError('Unrecognized public client key format')
    result[URL_KEY] = OFFICIAL_BACKEND
    result[PUBLIC_KEY] = key
    result.setdefault('TV_LOGIN_WEB_BASE_URL', 'https://nuvio.tv/tv-login')
    result.setdefault('DEVICE_LOGIN_WEB_BASE_URL', 'https://nuvio.tv/link')
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--properties', type=Path, default=Path('local.properties'))
    args = parser.parse_args()
    original = args.properties.read_text().splitlines() if args.properties.exists() else []
    properties = dict(line.split('=', 1) for line in original if '=' in line and not line.lstrip().startswith('#'))
    if properties.get(URL_KEY, '').strip() and properties.get(PUBLIC_KEY, '').strip():
        print('Existing account backend configuration preserved')
        return
    request = urllib.request.Request(DISCOVERY_URL, headers={'Accept': 'application/json'})
    with urllib.request.urlopen(request, timeout=20) as response:
        raw = response.read(64 * 1024 + 1)
    if len(raw) > 64 * 1024:
        raise ValueError('Discovery document is too large')
    configured = complete_backend(properties, json.loads(raw))
    added = {key: value for key, value in configured.items() if properties.get(key) != value}
    lines = [line for line in original if line.partition('=')[0] not in added]
    lines.extend(f'{key}={value}' for key, value in added.items())
    args.properties.write_text('\n'.join(lines) + '\n')
    args.properties.chmod(0o600)
    print('Nuvio QR login configured from the official public discovery endpoint')


if __name__ == '__main__':
    main()
