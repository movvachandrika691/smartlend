#!/bin/bash

# Script to generate RSA key pair for JWT signing
# Run this script once during initial setup

KEYS_DIR="src/main/resources/keys"
PRIVATE_KEY="$KEYS_DIR/private_key.pem"
PUBLIC_KEY="$KEYS_DIR/public_key.pem"

echo "Generating RSA key pair for JWT authentication..."

# Create keys directory if it doesn't exist
mkdir -p $KEYS_DIR

# Generate 2048-bit RSA private key
openssl genrsa -out $PRIVATE_KEY 2048

# Extract public key from private key
openssl rsa -in $PRIVATE_KEY -pubout -out $PUBLIC_KEY

# Set permissions (private key should be readable only by owner)
chmod 600 $PRIVATE_KEY
chmod 644 $PUBLIC_KEY

echo "Keys generated successfully:"
echo "  Private key: $PRIVATE_KEY"
echo "  Public key:  $PUBLIC_KEY"
echo ""
echo "IMPORTANT: Add private_key.pem to .gitignore!"
echo "Never commit private keys to version control."
