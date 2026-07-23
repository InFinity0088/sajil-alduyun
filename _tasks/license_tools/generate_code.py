"""
License code generator for OmniRoute Sajil al-Duyun app.

Usage:
    python3 generate_code.py <purpose>

Where <purpose> is one of:
    setup   - generate owner setup license code
    renew   - generate renewal license code

The generated code is a Base64-encoded RSA-2048 SHA256 signature.

Requirements:
    pip install cryptography
"""

import base64
import sys
import os
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding

# Path to the private key (keep this SECRET — never commit it)
KEY_PATH = os.path.join(os.path.dirname(__file__), "license_private.pem")


def generate_code(purpose_bytes: bytes) -> str:
    """Sign purpose_bytes with the private key and return a Base64 code."""
    if not os.path.exists(KEY_PATH):
        print(f"Error: private key not found at {KEY_PATH}", file=sys.stderr)
        sys.exit(1)

    with open(KEY_PATH, "rb") as f:
        private_key = serialization.load_pem_private_key(f.read(), password=None)

    signature = private_key.sign(purpose_bytes, padding.PKCS1v15(), hashes.SHA256())
    return base64.b64encode(signature).decode()


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)

    purpose_map = {
        "setup": b"SAJIL-OWNER-SETUP",
        "renew": b"SAJIL-LICENSE-RENEW",
    }

    arg = sys.argv[1].lower()
    if arg not in purpose_map:
        print(f"Unknown purpose '{arg}'. Valid: {', '.join(purpose_map.keys())}", file=sys.stderr)
        sys.exit(1)

    purpose = purpose_map[arg]
    code = generate_code(purpose)
    print(f"\nLicense code ({arg}):")
    print(code)
    print()


if __name__ == "__main__":
    main()
