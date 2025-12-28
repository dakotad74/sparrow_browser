#!/bin/bash

# P2P Exchange Status Checker
# Monitors both Sparrow instances for P2P functionality

echo "=========================================="
echo "P2P Exchange Status Check"
echo "=========================================="
echo ""

# Check if instances are running
echo "📊 Instance Status:"
echo ""

ALICE_PID=$(pgrep -f "sparrow.*\.sparrow[^-]" | head -1)
BOB_PID=$(pgrep -f "sparrow.*\.sparrow-bob" | head -1)

if [ -n "$ALICE_PID" ]; then
    echo "  ✅ Alice (PID: $ALICE_PID) - Running"
else
    echo "  ❌ Alice - Not running"
fi

if [ -n "$BOB_PID" ]; then
    echo "  ✅ Bob (PID: $BOB_PID) - Running"
else
    echo "  ❌ Bob - Not running"
fi

echo ""
echo "=========================================="
echo "Nostr Relay Connection Status"
echo "=========================================="
echo ""

# Check Alice's relay connections
if [ -f /tmp/sparrow-alice.log ]; then
    ALICE_RELAYS=$(grep -i "relays connected" /tmp/sparrow-alice.log | tail -1)
    if [ -n "$ALICE_RELAYS" ]; then
        echo "  Alice: $ALICE_RELAYS"
    else
        echo "  Alice: Checking connection..."
    fi
fi

# Check Bob's relay connections
if [ -f /tmp/sparrow-bob.log ]; then
    BOB_RELAYS=$(grep -i "relays connected" /tmp/sparrow-bob.log | tail -1)
    if [ -n "$BOB_RELAYS" ]; then
        echo "  Bob: $BOB_RELAYS"
    else
        echo "  Bob: Checking connection..."
    fi
fi

echo ""
echo "=========================================="
echo "P2P Service Status"
echo "=========================================="
echo ""

# Check if P2P service started
ALICE_P2P=$(grep -i "starting nostr p2p service\|nostr p2p service started" /tmp/sparrow-alice.log | tail -1)
BOB_P2P=$(grep -i "starting nostr p2p service\|nostr p2p service started" /tmp/sparrow-bob.log | tail -1)

if [ -n "$ALICE_P2P" ]; then
    echo "  ✅ Alice P2P Service: Active"
else
    echo "  ⏳ Alice P2P Service: Not detected"
fi

if [ -n "$BOB_P2P" ]; then
    echo "  ✅ Bob P2P Service: Active"
else
    echo "  ⏳ Bob P2P Service: Not detected"
fi

echo ""
echo "=========================================="
echo "Offer Subscription Status"
echo "=========================================="
echo ""

# Check if subscribed to offers
ALICE_SUB=$(grep -i "subscribing to p2p trade offers" /tmp/sparrow-alice.log | tail -1)
BOB_SUB=$(grep -i "subscribing to p2p trade offers" /tmp/sparrow-bob.log | tail -1)

if [ -n "$ALICE_SUB" ]; then
    echo "  ✅ Alice: Subscribed to marketplace offers"
else
    echo "  ⏳ Alice: Not yet subscribed"
fi

if [ -n "$BOB_SUB" ]; then
    echo "  ✅ Bob: Subscribed to marketplace offers"
else
    echo "  ⏳ Bob: Not yet subscribed"
fi

echo ""
echo "=========================================="
echo "Published Offers"
echo "=========================================="
echo ""

# Count published offers
ALICE_OFFERS=$(grep -c "Published offer to Nostr" /tmp/sparrow-alice.log 2>/dev/null || echo "0")
BOB_OFFERS=$(grep -c "Published offer to Nostr" /tmp/sparrow-bob.log 2>/dev/null || echo "0")

# Remove any newlines from counts
ALICE_OFFERS=$(echo "$ALICE_OFFERS" | tr -d '\n')
BOB_OFFERS=$(echo "$BOB_OFFERS" | tr -d '\n')

echo "  Alice published: $ALICE_OFFERS offer(s)"
echo "  Bob published: $BOB_OFFERS offer(s)"

# Show last published offer details
if [ "$ALICE_OFFERS" -gt 0 ] 2>/dev/null; then
    echo ""
    echo "  Alice's last offer:"
    grep "Published offer to Nostr" /tmp/sparrow-alice.log | tail -1 | sed 's/^/    /'
fi

if [ "$BOB_OFFERS" -gt 0 ] 2>/dev/null; then
    echo ""
    echo "  Bob's last offer:"
    grep "Published offer to Nostr" /tmp/sparrow-bob.log | tail -1 | sed 's/^/    /'
fi

echo ""
echo "=========================================="
echo "Received Offers"
echo "=========================================="
echo ""

# Count received offers
ALICE_RECEIVED=$(grep -c "Added offer to marketplace" /tmp/sparrow-alice.log 2>/dev/null || echo "0")
BOB_RECEIVED=$(grep -c "Added offer to marketplace" /tmp/sparrow-bob.log 2>/dev/null || echo "0")

# Remove any newlines from counts
ALICE_RECEIVED=$(echo "$ALICE_RECEIVED" | tr -d '\n')
BOB_RECEIVED=$(echo "$BOB_RECEIVED" | tr -d '\n')

echo "  Alice received: $ALICE_RECEIVED offer(s)"
echo "  Bob received: $BOB_RECEIVED offer(s)"

# Show last received offer details
if [ "$ALICE_RECEIVED" -gt 0 ] 2>/dev/null; then
    echo ""
    echo "  Alice's last received:"
    grep "Added offer to marketplace" /tmp/sparrow-alice.log | tail -1 | sed 's/^/    /'
fi

if [ "$BOB_RECEIVED" -gt 0 ] 2>/dev/null; then
    echo ""
    echo "  Bob's last received:"
    grep "Added offer to marketplace" /tmp/sparrow-bob.log | tail -1 | sed 's/^/    /'
fi

echo ""
echo "=========================================="
echo "Chat Activity"
echo "=========================================="
echo ""

# Check chat subscriptions
ALICE_CHAT=$(grep -c "Subscribed to conversation" /tmp/sparrow-alice.log 2>/dev/null || echo "0")
BOB_CHAT=$(grep -c "Subscribed to conversation" /tmp/sparrow-bob.log 2>/dev/null || echo "0")

echo "  Alice chat subscriptions: $ALICE_CHAT"
echo "  Bob chat subscriptions: $BOB_CHAT"

# Check sent messages
ALICE_SENT=$(grep -c "Sent encrypted message" /tmp/sparrow-alice.log 2>/dev/null || echo "0")
BOB_SENT=$(grep -c "Sent encrypted message" /tmp/sparrow-bob.log 2>/dev/null || echo "0")

echo "  Alice sent messages: $ALICE_SENT"
echo "  Bob sent messages: $BOB_SENT"

# Check received messages
ALICE_MSG=$(grep -c "Received encrypted message" /tmp/sparrow-alice.log 2>/dev/null || echo "0")
BOB_MSG=$(grep -c "Received encrypted message" /tmp/sparrow-bob.log 2>/dev/null || echo "0")

echo "  Alice received messages: $ALICE_MSG"
echo "  Bob received messages: $BOB_MSG"

echo ""
echo "=========================================="
echo "Recent Errors"
echo "=========================================="
echo ""

# Check for recent errors (last 10)
ALICE_ERRORS=$(grep -i "error\|exception" /tmp/sparrow-alice.log | grep -v "ERROR ===" | tail -5)
BOB_ERRORS=$(grep -i "error\|exception" /tmp/sparrow-bob.log | grep -v "ERROR ===" | tail -5)

if [ -n "$ALICE_ERRORS" ]; then
    echo "  Alice recent errors:"
    echo "$ALICE_ERRORS" | sed 's/^/    /'
else
    echo "  ✅ Alice: No recent errors"
fi

echo ""

if [ -n "$BOB_ERRORS" ]; then
    echo "  Bob recent errors:"
    echo "$BOB_ERRORS" | sed 's/^/    /'
else
    echo "  ✅ Bob: No recent errors"
fi

echo ""
echo "=========================================="
echo "Quick Commands"
echo "=========================================="
echo ""
echo "  Monitor Alice:   tail -f /tmp/sparrow-alice.log"
echo "  Monitor Bob:     tail -f /tmp/sparrow-bob.log"
echo "  Monitor both:    tail -f /tmp/sparrow-*.log"
echo "  Monitor P2P:     tail -f /tmp/sparrow-*.log | grep -i p2p"
echo "  Stop both:       kill $ALICE_PID $BOB_PID"
echo "  Restart:         ./run-two-instances.sh"
echo ""
echo "=========================================="
echo "Status check complete!"
echo "=========================================="
