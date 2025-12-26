# Demonstrating Coordination Features Without GUI

Since the development environment is headless and Phase 5 (UI) is not implemented, here are ways to demonstrate and verify the coordination features work correctly.

## Option 1: Run Integration Tests (Recommended)

The integration test demonstrates the full coordination workflow with two simulated participants.

### Run the test:

```bash
cd /home/r2d2/Desarrollo/SparrowDev/sparrow
./gradlew test --tests CoordinationIntegrationTest.testFullCoordinationWorkflow
```

### What it demonstrates:

1. ✅ Participant 1 creates a coordination session
2. ✅ Participant 2 discovers the session via Nostr events
3. ✅ Both participants join the session
4. ✅ Participant 1 proposes an output (50,000 sats to address1)
5. ✅ Participant 2 proposes an output (30,000 sats to address2)
6. ✅ Both participants propose fee rates (10.0 and 12.5 sat/vB)
7. ✅ Automatic fee consensus (12.5 - highest wins)
8. ✅ Session finalization
9. ✅ Events are fired to EventBus

### Expected output:

```
CoordinationIntegrationTest > testFullCoordinationWorkflow() PASSED
```

### View detailed test output:

```bash
./gradlew test --tests CoordinationIntegrationTest.testFullCoordinationWorkflow --info
```

## Option 2: Interactive Kotlin Script

Create a Kotlin script to interactively demonstrate coordination:

### Create demo script:

```bash
cat > /tmp/coordination-demo.kts << 'EOF'
#!/usr/bin/env kotlin

import com.sparrowwallet.sparrow.coordination.*
import com.sparrowwallet.drongo.Network
import com.sparrowwallet.drongo.wallet.Wallet

println("=== Sparrow Browser Coordination Demo ===\n")

// Create two coordination managers (simulating two users)
val manager1 = CoordinationSessionManager()
val manager2 = CoordinationSessionManager()

manager1.setMyNostrPubkey("participant1-pubkey-abc123")
manager2.setMyNostrPubkey("participant2-pubkey-xyz789")

println("Created two coordination managers\n")

// Participant 1 creates a session
val wallet1 = Wallet("Alice's Wallet")
wallet1.setNetwork(Network.TESTNET)

val session1 = manager1.createSession(wallet1, 2)
val sessionId = session1.getSessionId()

println("✅ Participant 1 created session: $sessionId")
println("   Expected participants: ${session1.getExpectedParticipants()}")
println("   Session state: ${session1.getState()}\n")

// Simulate session discovery (in real app, this would happen via Nostr)
val wallet2 = Wallet("Bob's Wallet")
wallet2.setNetwork(Network.TESTNET)

println("✅ Participant 2 discovered session\n")

// Both participants join
manager1.joinSession(sessionId, wallet1, "participant1-pubkey-abc123")
println("✅ Participant 1 joined session")

manager2.joinSession(sessionId, wallet2, "participant2-pubkey-xyz789")
println("✅ Participant 2 joined session")

println("   Total participants: ${session1.getParticipants().size}")
println("   All joined: ${session1.allParticipantsJoined()}\n")

// NOTE: In real implementation with Nostr, we would need to manually sync
// session2 by handling events. For demo, we'll work with session1 only.

println("=== Output Proposals ===\n")

// Participant 1 proposes an output
val addr1 = Address.fromString("tb1q9pvjqz5u5sdgpatg3wn0ce438u5cyv85lly0pc")
manager1.proposeOutput(sessionId, addr1, 50000, "Payment to Alice", "participant1-pubkey-abc123")
println("✅ Participant 1 proposed: 50,000 sats to ${addr1.toString()}")

// Participant 2 proposes an output
val addr2 = Address.fromString("tb1qw508d6qejxtdg4y5r3zarvary0c5xw7kxpjzsx")
manager1.proposeOutput(sessionId, addr2, 30000, "Payment to Bob", "participant2-pubkey-xyz789")
println("✅ Participant 2 proposed: 30,000 sats to ${addr2.toString()}")

println("\n   Total outputs: ${session1.getOutputs().size}")
println("   Total amount: ${session1.getTotalOutputAmount()} sats\n")

println("=== Fee Proposals ===\n")

// Both propose fees
manager1.proposeFee(sessionId, 10.0, "participant1-pubkey-abc123")
println("✅ Participant 1 proposed fee: 10.0 sat/vB")

manager1.proposeFee(sessionId, 12.5, "participant2-pubkey-xyz789")
println("✅ Participant 2 proposed fee: 12.5 sat/vB")

println("\n   All participants proposed fees: ${session1.allParticipantsProposedFees()}")
println("   Agreed fee rate: ${session1.getAgreedFeeRate()} sat/vB")
println("   (Automatic consensus: highest fee)\n")

println("=== Session Finalization ===\n")

println("   Ready to finalize: ${session1.isReadyToFinalize()}")
manager1.finalizeSession(sessionId)
println("✅ Session finalized")
println("   Session state: ${session1.getState()}\n")

println("=== Summary ===\n")
session1.getOutputs().forEachIndexed { i, output ->
    println("   Output ${i+1}: ${output.getAmount()} sats → ${output.getAddress()}")
}
println("\n   Total: ${session1.getTotalOutputAmount()} sats")
println("   Fee rate: ${session1.getAgreedFeeRate()} sat/vB")
println("   Participants: ${session1.getParticipants().size}")
println("\n✅ Coordination complete! Ready for PSBT creation (Phase 4)")
EOF
```

**Note**: This script would require proper classpath setup to work. Included for illustration.

## Option 3: Examine Test Output in Detail

Run tests with verbose output to see all coordination events:

```bash
./gradlew test --tests CoordinationIntegrationTest --info 2>&1 | grep -A5 -B5 "Coordination"
```

Look for log messages like:
- "Published session-create event for session: ..."
- "Handling session-join message for session: ..."
- "Added participant to session: ..."
- "Proposing output for session: ..."
- "All participants have proposed fees"
- "Session finalized: ..."

## Option 4: Debug Mode with Logging

Modify the test to add detailed logging:

```bash
# Edit CoordinationIntegrationTest.java to add verbose logging
vim src/test/java/com/sparrowwallet/sparrow/coordination/CoordinationIntegrationTest.java
```

Add after each operation:
```java
System.out.println("Session state: " + session1.getState());
System.out.println("Participants: " + session1.getParticipants().size());
System.out.println("Outputs: " + session1.getOutputs());
System.out.println("Fee proposals: " + session1.getFeeProposals());
```

Then run:
```bash
./gradlew test --tests CoordinationIntegrationTest.testFullCoordinationWorkflow
```

## Option 5: Inspect Test Data Structures

After test runs, examine the session object:

```java
@Test
public void testFullCoordinationWorkflow() throws Exception {
    // ... existing test code ...

    // At the end, print detailed session info
    System.out.println("\n=== SESSION DETAILS ===");
    System.out.println("Session ID: " + session1.getSessionId());
    System.out.println("State: " + session1.getState());
    System.out.println("Network: " + session1.getNetwork());
    System.out.println("Expected participants: " + session1.getExpectedParticipants());
    System.out.println("Actual participants: " + session1.getParticipants().size());

    System.out.println("\nParticipants:");
    for(CoordinationParticipant p : session1.getParticipants()) {
        System.out.println("  - " + p.getName() + " (" + p.getPubkey() + ")");
    }

    System.out.println("\nOutputs:");
    for(CoordinationOutput o : session1.getOutputs()) {
        System.out.println("  - " + o.getAmount() + " sats → " + o.getAddress());
        System.out.println("    Proposed by: " + o.getProposedBy());
        System.out.println("    Label: " + o.getLabel());
    }

    System.out.println("\nFee Proposals:");
    for(CoordinationFeeProposal f : session1.getFeeProposals()) {
        System.out.println("  - " + f.getFeeRate() + " sat/vB by " + f.getProposedBy());
    }

    System.out.println("\nAgreed Fee: " + session1.getAgreedFeeRate() + " sat/vB");
    System.out.println("Total Output Amount: " + session1.getTotalOutputAmount() + " sats");
    System.out.println("Ready to Finalize: " + session1.isReadyToFinalize());
}
```

## Option 6: Mock Nostr Event Inspection

The `MockNostrRelay` in tests actually publishes NostrEvent objects. Add logging to see the actual Nostr messages:

```java
@Override
public void publishEvent(NostrEvent event) {
    System.out.println("\n=== NOSTR EVENT PUBLISHED ===");
    System.out.println("Kind: " + event.getKind());
    System.out.println("Pubkey: " + event.getPubkey());
    System.out.println("Message Type: " + event.getTagValue("d"));
    System.out.println("Tags: " + event.getTags());
    System.out.println("Content: " + event.getContent());
    System.out.println("========================\n");

    NostrMessageReceivedEvent receivedEvent =
        new NostrMessageReceivedEvent(event, "mock://relay");
    EventManager.get().post(receivedEvent);
}
```

This will show exactly what Nostr messages are being exchanged.

## Option 7: Watch Event Flow

Subscribe to all coordination events in the test:

```java
private static class VerboseEventCollector {
    @Subscribe
    public void onSessionCreated(CoordinationSessionCreatedEvent event) {
        System.out.println("📢 EVENT: Session created - " + event.getSession().getSessionId());
    }

    @Subscribe
    public void onParticipantJoined(CoordinationParticipantJoinedEvent event) {
        System.out.println("📢 EVENT: Participant joined - " + event.getParticipant().getName());
    }

    @Subscribe
    public void onOutputProposed(CoordinationOutputProposedEvent event) {
        System.out.println("📢 EVENT: Output proposed - " + event.getOutput().getAmount() + " sats");
    }

    @Subscribe
    public void onFeeProposed(CoordinationFeeProposedEvent event) {
        System.out.println("📢 EVENT: Fee proposed - " + event.getFeeProposal().getFeeRate() + " sat/vB");
    }

    @Subscribe
    public void onFeeAgreed(CoordinationFeeAgreedEvent event) {
        System.out.println("📢 EVENT: Fee agreed - " + event.getFeeRate() + " sat/vB");
    }

    @Subscribe
    public void onStateChanged(CoordinationSessionStateChangedEvent event) {
        System.out.println("📢 EVENT: State changed - " + event.getNewState());
    }

    @Subscribe
    public void onFinalized(CoordinationFinalizedEvent event) {
        System.out.println("📢 EVENT: Session finalized - " + event.getSessionId());
    }
}
```

Register in test:
```java
VerboseEventCollector collector = new VerboseEventCollector();
EventManager.get().register(collector);
```

## Demonstration Workflow

Here's the complete workflow you can demonstrate with tests:

### Step 1: Setup
```bash
cd /home/r2d2/Desarrollo/SparrowDev/sparrow
```

### Step 2: Run Unit Test
```bash
./gradlew test --tests CoordinationWorkflowTest.testFeeProposalReplacement
```

Shows: Fee proposal replacement logic ✅

### Step 3: Run Integration Test
```bash
./gradlew test --tests CoordinationIntegrationTest
```

Shows:
- ✅ Full coordination workflow
- ✅ MockNostrRelay message relay
- ✅ Event publishing and parsing
- ✅ State transitions
- ✅ Output coordination
- ✅ Fee consensus
- ✅ Session finalization

### Step 4: Check Test Report
```bash
open build/reports/tests/test/index.html
# Or for headless:
cat build/test-results/test/TEST-com.sparrowwallet.sparrow.coordination.CoordinationIntegrationTest.xml
```

## Visual Representation of What Tests Demonstrate

```
┌─────────────────────────────────────────────────────────────────┐
│                  COORDINATION WORKFLOW TEST                     │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  1. CREATE SESSION                                              │
│     Manager1.createSession(wallet, 2)                           │
│     └─> publishSessionCreateEvent()                             │
│         └─> NostrEvent(kind:38383, d:session-create)            │
│             └─> MockNostrRelay.publishEvent()                   │
│                 └─> EventBus.post(NostrMessageReceivedEvent)    │
│                     └─> Manager2.onNostrMessage()               │
│                         └─> handleSessionCreateMessage()        │
│                             └─> session2 created! ✅            │
│                                                                 │
│  2. JOIN SESSION                                                │
│     Manager1.joinSession(sessionId, wallet, pubkey1)            │
│     Manager2.joinSession(sessionId, wallet, pubkey2)            │
│     └─> Both sessions have 2 participants ✅                    │
│         Session state: JOINING                                  │
│                                                                 │
│  3. PROPOSE OUTPUTS                                             │
│     Manager1.proposeOutput(sessionId, addr1, 50000)             │
│     Manager2.proposeOutput(sessionId, addr2, 30000)             │
│     └─> Both sessions have 2 outputs ✅                         │
│         Total: 80,000 sats                                      │
│         Session state: PROPOSING                                │
│                                                                 │
│  4. PROPOSE FEES                                                │
│     Manager1.proposeFee(sessionId, 10.0)                        │
│     Manager2.proposeFee(sessionId, 12.5)                        │
│     └─> Automatic consensus: 12.5 sat/vB ✅                     │
│         Session state: AGREEING                                 │
│                                                                 │
│  5. FINALIZE                                                    │
│     Manager1.finalizeSession(sessionId)                         │
│     └─> Both sessions finalized ✅                              │
│         Session state: FINALIZED                                │
│         Ready for PSBT creation (Phase 4)                       │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

## Summary

Even without a GUI, the coordination features are fully demonstrable through:

✅ **Automated tests** - Complete workflow coverage
✅ **Event logging** - See all coordination messages
✅ **State inspection** - Examine session objects
✅ **Mock relay** - Simulates real Nostr communication
✅ **Event flow** - Track EventBus messages

The tests prove that the backend coordination logic is **complete and functional**. Once Phase 5 (UI) is implemented, the same logic will power the graphical coordination wizard.

## Next: Running with GUI

To see the actual Sparrow wallet interface (though coordination UI not yet implemented):

1. **Copy project** to a machine with graphical display
2. **Run**: `./gradlew run`
3. **See**: Standard Sparrow wallet interface
4. **Note**: "Coordinate Transaction" button not yet added (Phase 5)

See [RUNNING_GUI.md](RUNNING_GUI.md) for details.
