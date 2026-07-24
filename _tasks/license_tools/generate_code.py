"""
License code generator for OmniRoute Sajil al-Duyun app.

Generates RSA-2048 SHA256 signed license codes for the app's license system.
Codes are Base64-encoded signatures — embed the duration in the signed purpose
string so the same app can accept 3-day, 6-month, and unlimited codes.

Usage:
    python3 generate_code.py setup                      # 6-month setup (backward compat)
    python3 generate_code.py setup:3d                   # 3-day setup
    python3 generate_code.py setup:6m                   # 6-month setup
    python3 generate_code.py setup:unlimited            # Unlimited setup

    python3 generate_code.py setup:6m --device a1b2c3d  # Machine-locked to device
    python3 generate_code.py renew:6m --device a1b2c3d  # Machine-locked renewal

    python3 generate_code.py renew                      # 6-month renewal (backward compat)
    python3 generate_code.py renew:3d                   # 3-day renewal
    python3 generate_code.py renew:6m                   # 6-month renewal
    python3 generate_code.py renew:unlimited            # Unlimited renewal

Duration aliases:
    3d  =  3 days            (259,200,000 ms)
    6m  =  ~6 months         (15,552,000,000 ms)
    unlimited  =  never expires  (Long.MAX_VALUE)

With --device, the signed purpose becomes:
  SAJIL-OWNER-SETUP:<duration_ms>:<device_id>
  SAJIL-LICENSE-RENEW:<duration_ms>:<device_id>

This means the code ONLY works on the device with that Android ID.
Without --device, codes are device-agnostic (backward compatible).

Requirements:
    pip install cryptography
"""

import argparse
import base64
import sys
import os
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding

# Path to the private key (keep this SECRET — never commit it)
KEY_PATH = os.path.join(os.path.dirname(__file__), "license_private.pem")

# Duration aliases → milliseconds (must match LicenseVerifier.KNOWN_DURATIONS)
DURATION_ALIASES = {
    "3d": 3 * 24 * 60 * 60 * 1000,                              # 259,200,000
    "6m": 6 * 30 * 24 * 60 * 60 * 1000,                         # 15,552,000,000
    "unlimited": 9223372036854775807,                            # Long.MAX_VALUE
}

DURATION_LABELS = {
    "3d": "3 أيام (3 days)",
    "6m": "6 أشهر (6 months)",
    "unlimited": "غير محدود (Never expires)",
}


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
    parser = argparse.ArgumentParser(
        description="Generate RSA-signed license codes for Sajil al-Duyun.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  python3 generate_code.py setup:6m
  python3 generate_code.py setup:unlimited --device a1b2c3d4e5f6
  python3 generate_code.py renew:3d
  python3 generate_code.py renew:unlimited --device a1b2c3d4e5f6
        """,
    )
    parser.add_argument(
        "purpose",
        help='Code type with optional duration, e.g. "setup:6m", "renew:unlimited"',
    )
    parser.add_argument(
        "--device",
        metavar="ANDROID_ID",
        help="Lock the code to a specific device (Android ID). Without this,"
             " the code works on any device (legacy mode).",
    )
parser.add_argument(
        "--list-durations",
        action="store_true",
        help="Show available duration aliases and exit",
    )

    args = parser.parse_args()

    if args.list_durations:
        print("Available duration aliases:\n")
        for alias, ms in DURATION_ALIASES.items():
            label = DURATION_LABELS.get(alias, "")
            print(f"  {alias:10s}  {ms:>20d} ms   {label}")
        print()
        sys.exit(0)

    # Parse the purpose argument
    purpose_arg = args.purpose.lower()

    # Determine prefix (setup / renew / transfer / revoke)
    if purpose_arg.startswith("setup"):
        prefix = "SAJIL-OWNER-SETUP"
    elif purpose_arg.startswith("renew"):
        prefix = "SAJIL-LICENSE-RENEW"
    elif purpose_arg.startswith("transfer"):
        prefix = "SAJIL-LICENSE-TRANSFER"
    elif purpose_arg.startswith("revoke"):
        # revoke has no duration — purpose is just SAJIL-LICENSE-REVOKE:<device_id>
        revoke_prefix = "SAJIL-LICENSE-REVOKE"
        if not args.device:
            print("Error: revoke requires --device", file=sys.stderr)
            sys.exit(1)
        purpose = f"{revoke_prefix}:{args.device}"
        print(f"\n  Purpose:      {purpose}")
        print(f"  Device-lock:  Yes — {args.device}")
        print("\nLicense code (revoke):")
        print(generate_code(purpose.encode()))
        print()
        sys.exit(0)
    else:
        print(f"Error: unknown purpose '{args.purpose}' — must be setup, renew, transfer, or revoke",
              file=sys.stderr)
        sys.exit(1)

    # Determine duration
    if ":" in purpose_arg:
        _, dur_str = purpose_arg.split(":", 1)
        if dur_str not in DURATION_ALIASES:
            print(f"Error: unknown duration '{dur_str}'."
                  f" Valid: {', '.join(DURATION_ALIASES.keys())}",
                  file=sys.stderr)
            sys.exit(1)
        duration_ms = DURATION_ALIASES[dur_str]
        duration_label = dur_str
    else:
        # Bare purpose (backward compat) — app defaults to 6 months
        duration_label = "6m (legacy)"
        duration_ms = None

    # Build the purpose string
    if duration_ms is not None:
        base_purpose = f"{prefix}:{duration_ms}"
    else:
        base_purpose = prefix

    if args.device:
        full_purpose = f"{base_purpose}:{args.device}"
    else:
        full_purpose = base_purpose

    # Sign it
    code = generate_code(full_purpose.encode())

    # Print summary
    device_lock = "No (works on any device)"
    if args.device:
        device_lock = f"Yes — {args.device}"
    print()
    print(f"  Purpose:      {full_purpose}")
    print(f"  Duration:     {duration_label} ({DURATION_LABELS.get(duration_label.replace(' (legacy)', ''), 'N/A')})")
    print(f"  Device-lock:  {device_lock}")
    print()
    print("License code:")
    print(code)
    print()


if __name__ == "__main__":
    main()
