#!/usr/bin/env python3
import base64
import hashlib
import json
import os
import secrets
import sys
import urllib.parse
import urllib.request
import webbrowser

CLIENT_ID = "6BDSRdpK9hqEBTgU"
CLIENT_SECRET = "xeuPmY7nbpZ9IIbLAcQ93shka1VNheUAqN6IcszjTG8="
REDIRECT_URI = "https://tidal.com/android/login/auth"
TOKEN_URL = "https://auth.tidal.com/v1/oauth2/token"

def generate_pkce():
    verifier = base64.urlsafe_b64encode(secrets.token_bytes(32)).decode("utf-8").rstrip("=")
    digest = hashlib.sha256(verifier.encode("utf-8")).digest()
    challenge = base64.urlsafe_b64encode(digest).decode("utf-8").rstrip("=")
    return verifier, challenge

def build_auth_url(challenge):
    params = {
        "response_type": "code",
        "redirect_uri": REDIRECT_URI,
        "client_id": CLIENT_ID,
        "lang": "EN",
        "appMode": "android",
        "code_challenge": challenge,
        "code_challenge_method": "S256",
    }
    return "https://login.tidal.com/authorize?" + urllib.parse.urlencode(params)

def exchange_code(code, verifier):
    data = urllib.parse.urlencode({
        "grant_type": "authorization_code",
        "client_id": CLIENT_ID,
        "client_secret": CLIENT_SECRET,
        "code": code,
        "code_verifier": verifier,
        "redirect_uri": REDIRECT_URI,
        "scope": "r_usr w_usr w_sub",
    }).encode("utf-8")
    req = urllib.request.Request(TOKEN_URL, data=data, headers={"Content-Type": "application/x-www-form-urlencoded"})
    with urllib.request.urlopen(req) as resp:
        return json.loads(resp.read().decode("utf-8"))

def main():
    verifier, challenge = generate_pkce()
    auth_url = build_auth_url(challenge)

    print("=" * 60)
    print("Inicio de sesión en TIDAL para Pomelo Desktop")
    print("=" * 60)
    print("\nAbriendo el navegador para iniciar sesión en TIDAL...\n")
    print(f"URL de autenticación:\n{auth_url}\n")
    try:
        webbrowser.open(auth_url)
    except Exception:
        pass

    print("Instrucciones:")
    print("1. Inicia sesión en TIDAL en la página que se abrió en tu navegador.")
    print("2. Al finalizar, el navegador intentará redirigir a 'https://tidal.com/android/login/auth?code=...'")
    print("3. Copia la URL completa de la barra de direcciones de tu navegador y pégala aquí.")
    print("-" * 60)

    try:
        user_input = input("Pega aquí la URL redirigida (o el código): ").strip()
    except (KeyboardInterrupt, EOFError):
        print("\nCancelado.")
        sys.exit(1)

    code = user_input
    if "code=" in user_input:
        parsed = urllib.parse.urlparse(user_input)
        params = urllib.parse.parse_qs(parsed.query)
        code = params.get("code", [user_input])[0]

    if not code:
        print("Error: No se encontró ningún código de autorización.")
        sys.exit(1)

    print("\nIntercambiando código por tokens de sesión...")
    try:
        token_data = exchange_code(code, verifier)
    except Exception as e:
        print(f"Error al obtener token de TIDAL: {e}")
        sys.exit(1)

    config_dir = os.path.expanduser("~/.config/pomelo")
    os.makedirs(config_dir, exist_ok=True)
    session_file = os.path.join(config_dir, "session.json")

    access_token = token_data.get("access_token", "")
    refresh_token = token_data.get("refresh_token", "")
    user = token_data.get("user", {})
    user_id = str(user.get("userId", token_data.get("user_id", "")))
    country_code = user.get("countryCode", "US")

    session = {
        "access_token": access_token,
        "refresh_token": refresh_token,
        "country_code": country_code,
        "user_id": user_id,
    }

    with open(session_file, "w", encoding="utf-8") as f:
        json.dump(session, f, indent=2)

    print(f"\n¡Éxito! Sesión guardada en {session_file}")
    print(f"Usuario ID: {user_id} | País: {country_code}")
    print("\nAhora puedes ejecutar './build-desktop/orchard' con tu cuenta de TIDAL activa.")

if __name__ == "__main__":
    main()
