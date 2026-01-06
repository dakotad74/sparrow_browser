# MuSig2 Manual UI Testing Plan

**Date**: 2026-01-01
**Tester**: [To be filled]
**Environment**: Sparrow Wallet development build
**Status**: IN PROGRESS

---

## Testing Objectives

1. ✅ Verify MuSig2Round1Dialog opens and functions correctly
2. ✅ Verify MuSig2Round2Dialog opens and functions correctly
3. ✅ Test encrypted wallet support (both dialogs)
4. ✅ Test error scenarios (wrong password, cancel, etc.)
5. ✅ Verify complete MuSig2 signing workflow
6. ✅ Test PSBT integration with UI

---

## Prerequisites

### Required Files

- ✅ MuSig2Round1Dialog.java (implemented)
- ✅ MuSig2Round2Dialog.java (implemented)
- ✅ MuSig2 core library (all tests passing)
- ✅ PSBT-MuSig2 integration (complete)

### Test Wallets Needed

1. **Unencrypted Test Wallet**
   - Type: Taproot MuSig2 (2-of-2 or similar)
   - Purpose: Basic functionality testing

2. **Encrypted Test Wallet**
   - Type: Taproot MuSig2 (2-of-2 or similar)
   - Password: [Set during testing]
   - Purpose: Encrypted wallet support testing

### Test PSBT Needed

- PSBT requiring MuSig2 signatures
- At least 1 input
- Can be created manually or loaded from file

---

## Test Cases

## TEST CASE 1: MuSig2Round1Dialog - Unencrypted Wallet

### Preconditions
1. Open Sparrow Wallet
2. Load/create unencrypted MuSig2 wallet
3. Have a PSBT ready

### Steps
1. Open PSBT in Sparrow
2. Look for "Sign MuSig2" option
3. Click to open MuSig2Round1Dialog

### Expected Results
- [ ] Dialog opens with title "MuSig2 Round 1 - Nonce Exchange"
- [ ] Instructions display correctly
- [ ] "Generate My Nonces" button is visible and enabled
- [ ] Status label shows "Click 'Generate My Nonces' to begin"
- [ ] "My Public Nonces" text area is empty and read-only
- [ ] "Enter nonces from other signers" text area is empty
- [ ] "Continue to Round 2" button is disabled

### Action: Generate Nonces
1. Click "Generate My Nonces"

### Expected Results
- [ ] Button becomes disabled
- [ ] Status shows "Generating nonces..." or similar
- [ ] Nonces appear in "My Public Nonces" text area
- [ ] Format: `0:02abcd1234567890abcdef...` (one per input)
- [ ] Status shows success message (green)
- [ ] "Continue to Round 2" button remains disabled (waiting for other nonces)

### Action: Enter Other Nonces
1. Enter nonce in format: `0:02abcd1234567890abcdef...`
2. Press Enter

### Expected Results
- [ ] "Continue to Round 2" button becomes enabled
- [ ] No error messages

### Action: Continue to Round 2
1. Click "Continue to Round 2"

### Expected Results
- [ ] Dialog closes
- [ ] MuSig2Round2Dialog opens
- [ ] Round 1 data is preserved

### Result
**Status**: ⏳ PENDING
**Notes**: _________________________________________________

---

## TEST CASE 2: MuSig2Round1Dialog - Encrypted Wallet

### Preconditions
1. Open Sparrow Wallet
2. Load/create ENCRYPTED MuSig2 wallet (with password)
3. Have a PSBT ready

### Steps
1. Open PSBT in Sparrow
2. Click "Sign MuSig2" to open MuSig2Round1Dialog

### Expected Results
- [ ] Dialog opens normally (same as unencrypted)

### Action: Generate Nonces with Encrypted Wallet
1. Click "Generate My Nonces"

### Expected Results
- [ ] Status label shows "Wallet is encrypted. Please enter password to decrypt."
- [ ] Status label is orange color (#f39c12)
- [ ] PasswordDialog appears (modal)
- [ ] PasswordDialog has password field
- [ ] PasswordDialog has "OK" and "Cancel" buttons

### Sub-Test 2a: Correct Password
1. Enter correct password
2. Click OK

### Expected Results
- [ ] PasswordDialog closes
- [ ] Status shows "Generating nonces..." or similar
- [ ] Nonces appear in text area
- [ ] Status shows success (green)
- [ ] No errors in logs
- [ ] Log entry: "Wallet decrypted successfully"
- [ ] Log entry: "Cleared private keys from decrypted wallet"

### Sub-Test 2b: Wrong Password
1. Close and reopen dialog
2. Click "Generate My Nonces"
3. Enter WRONG password
4. Click OK

### Expected Results
- [ ] Error message: "Decryption failed: Wrong password" (or similar)
- [ ] Error message is red color
- [ ] "Generate My Nonces" button is re-enabled
- [ ] Can try again

### Sub-Test 2c: Cancel Password Entry
1. Close and reopen dialog
2. Click "Generate My Nonces"
3. Click Cancel in password dialog

### Expected Results
- [ ] Status shows "Password entry cancelled."
- [ ] Status is red color
- [ ] "Generate My Nonces" button is re-enabled
- [ ] No crash or exception

### Result
**Status**: ⏳ PENDING
**Notes**: _________________________________________________

---

## TEST CASE 3: MuSig2Round2Dialog - Unencrypted Wallet

### Preconditions
1. Complete Round 1 successfully
2. Have all nonces (my + other signers)
3. MuSig2Round2Dialog opens

### Expected Results
- [ ] Dialog opens with title "MuSig2 Round 2 - Create Partial Signature"
- [ ] Instructions display correctly
- [ ] "Create Partial Signature" button is visible and enabled
- [ ] Progress bar is hidden initially
- [ ] Status label shows "Ready to create partial signature"

### Action: Create Partial Signature
1. Click "Create Partial Signature"

### Expected Results
- [ ] Button becomes disabled
- [ ] Progress bar appears and starts at 0.1
- [ ] Status shows "Creating partial signature for input 1..."
- [ ] Progress bar advances (0.3 → 0.5+)
- [ ] For multiple inputs: "Creating partial signature for input 2..." etc.

### Final Results
- [ ] Status shows "Partial signature created successfully!" (green)
- [ ] Progress bar reaches 1.0
- [ ] Dialog closes after ~1.5 seconds
- [ ] PSBT now contains partial signature
- [ ] Can verify in PSBT viewer
- [ ] Log entry: "Created MuSig2 partial signature for input 0"

### Result
**Status**: ⏳ PENDING
**Notes**: _________________________________________________

---

## TEST CASE 4: MuSig2Round2Dialog - Encrypted Wallet

### Preconditions
1. Complete Round 1 with encrypted wallet
2. MuSig2Round2Dialog opens

### Action: Create Partial Signature with Encrypted Wallet
1. Click "Create Partial Signature"

### Expected Results
- [ ] Button becomes disabled
- [ ] Progress bar appears (0.1)
- [ ] Status shows "Wallet is encrypted. Password required..." (orange)
- [ ] PasswordDialog appears (modal)

### Sub-Test 4a: Correct Password
1. Enter correct password
2. Click OK

### Expected Results
- [ ] PasswordDialog closes
- [ ] Progress bar advances (0.2 → 0.3)
- [ ] Status shows "Creating partial signature for input 1..."
- [ ] Partial signature created successfully
- [ ] Success message (green)
- [ ] Dialog closes after 1.5 seconds
- [ ] Log entry: "Wallet decrypted successfully, creating partial signature"
- [ ] Log entry: "Cleared private keys from decrypted wallet"

### Sub-Test 4b: Wrong Password
1. Reopen dialog
2. Click "Create Partial Signature"
3. Enter WRONG password
4. Click OK

### Expected Results
- [ ] Error message: "Decryption failed: Wrong password" (red)
- [ ] Progress bar resets to 0
- [ ] Progress bar hidden
- [ ] "Create Partial Signature" button re-enabled
- [ ] Can try again

### Sub-Test 4c: Cancel Password Entry
1. Reopen dialog
2. Click "Create Partial Signature"
3. Click Cancel

### Expected Results
- [ ] Status shows "Password entry cancelled." (red)
- [ ] Progress bar resets to 0
- [ ] Progress bar hidden
- [ ] Button re-enabled

### Result
**Status**: ⏳ PENDING
**Notes**: _________________________________________________

---

## TEST CASE 5: Complete Workflow - 2-of-2 MuSig2

### Preconditions
1. Two MuSig2 wallets (signer1 and signer2)
2. PSBT requiring 2-of-2 signatures
3. Both wallets loaded in Sparrow

### Steps - Signer 1
1. Open PSBT with signer1 wallet
2. Execute Round 1:
   - Generate nonces
   - Copy my nonces
3. Execute Round 2:
   - Enter signer2's nonces
   - Create partial signature
4. Save PSBT

### Steps - Signer 2
1. Open PSBT with signer2 wallet
2. Execute Round 1:
   - Generate nonces
   - Copy my nonces
3. Execute Round 2:
   - Enter signer1's nonces
   - Create partial signature
4. Save PSBT

### Expected Results
- [ ] Both signers can complete Round 1
- [ ] Both signers can complete Round 2
- [ ] PSBT contains 2 partial signatures
- [ ] Final PSBT is valid and ready to broadcast
- [ ] Can verify in PSBT viewer

### Result
**Status**: ⏳ PENDING
**Notes**: _________________________________________________

---

## TEST CASE 6: Error Handling - Edge Cases

### 6a: Invalid Nonce Format
1. Open Round 1 dialog
2. Generate my nonces
3. Enter invalid nonce in "other nonces" field:
   - Test: `0:invalid` (not hex)
   - Test: `0:02abc` (too short)
   - Test: `invalid` (wrong format)

### Expected Results
- [ ] Error message for invalid format
- [ ] "Continue to Round 2" button remains disabled
- [ ] Clear error message (red)

### 6b: Empty Nonce Field
1. Open Round 1 dialog
2. Generate my nonces
3. Try to continue without entering other nonces

### Expected Results
- [ ] "Continue to Round 2" button stays disabled
- [ ] No crash when clicking

### 6c: Network/IO Errors (if applicable)
1. Simulate wallet loading error
2. Attempt to generate nonces

### Expected Results
- [ ] Graceful error message
- [ ] No crash
- [ ] Can retry

### Result
**Status**: ⏳ PENDING
**Notes**: _________________________________________________

---

## TEST CASE 7: UI Responsiveness

### 7a: Dialog Sizing
- [ ] Dialog fits on screen (not too large)
- [ ] Scrollbars appear if content is long
- [ ] All elements visible

### 7b: Button States
- [ ] Buttons disable during operations
- [ ] Buttons re-enable after errors
- [ ] Cancel button always works

### 7c: Progress Indication
- [ ] Progress bar visible during operations
- [ ] Progress bar advances smoothly
- [ ] Status messages update in real-time

### 7d: Thread Safety
- [ ] UI doesn't freeze during nonce generation
- [ ] UI doesn't freeze during decryption
- [ ] UI doesn't freeze during signature creation

### Result
**Status**: ⏳ PENDING
**Notes**: _________________________________________________

---

## TEST CASE 8: PSBT Integration

### Preconditions
1. Complete MuSig2 signing workflow
2. Final PSBT with partial signatures

### Verification
1. Open PSBT in PSBT viewer
2. Check inputs for MuSig2 data

### Expected Results
- [ ] PSBT contains MuSig2 partial signatures
- [ ] Partial signatures are in correct format
- [ ] Can combine with other signer's signatures
- [ ] Final signature verifies successfully
- [ ] Transaction can be broadcast (if fully signed)

### Result
**Status**: ⏳ PENDING
**Notes**: _________________________________________________

---

## Test Execution Log

### Session Information
- **Date/Time**: ___________________
- **Sparrow Version**: Development build
- **Java Version**: ___________________
- **OS**: ___________________
- **Tester**: ___________________

### Test Results Summary

| Test Case | Status | Issues Found | Severity |
|-----------|--------|--------------|----------|
| TC1: Round 1 - Unencrypted | ⏳ | | |
| TC2: Round 1 - Encrypted | ⏳ | | |
| TC3: Round 2 - Unencrypted | ⏳ | | |
| TC4: Round 2 - Encrypted | ⏳ | | |
| TC5: Complete 2-of-2 Workflow | ⏳ | | |
| TC6: Error Handling | ⏳ | | |
| TC7: UI Responsiveness | ⏳ | | |
| TC8: PSBT Integration | ⏳ | | |

### Issues Found

#### Issue #1
- **Title**: ___________________
- **Description**: ___________________
- **Steps to Reproduce**: ___________________
- **Expected**: ___________________
- **Actual**: ___________________
- **Severity**: ☐ Critical ☐ Major ☐ Minor ☐ Trivial
- **Status**: ⏳ Open ☐ Fixed ☐ Won't Fix

#### Issue #2
- **Title**: ___________________
- **Description**: ___________________
- **Steps to Reproduce**: ___________________
- **Expected**: ___________________
- **Actual**: ___________________
- **Severity**: ☐ Critical ☐ Major ☐ Minor ☐ Trivial
- **Status**: ⏳ Open ☐ Fixed ☐ Won't Fix

---

## Overall Assessment

### Criteria

- [ ] **Functionality**: All features work as expected
- [ ] **Security**: Encrypted wallet support is secure
- [ ] **User Experience**: Clear, intuitive interface
- [ ] **Error Handling**: Graceful handling of all errors
- [ ] **Performance**: No UI freezes or hangs
- [ ] **Integration**: PSBT integration works correctly

### Final Verdict

☐ **PASS** - Ready for production
☐ **PASS WITH MINOR ISSUES** - Document issues, can proceed
☐ **FAIL** - Critical issues found, must fix before production

### Tester Comments

___________________________________________________________________

___________________________________________________________________

___________________________________________________________________

---

## Post-Testing Actions

### If All Tests Pass
- [ ] Mark MuSig2 as production-ready
- [ ] Create user documentation
- [ ] Prepare for merge/release

### If Tests Fail
- [ ] Document all issues
- [ ] Prioritize by severity
- [ ] Fix critical issues
- [ ] Re-test after fixes
- [ ] Document workarounds (if any)

---

**Test Plan Version**: 1.0
**Last Updated**: 2026-01-01
**Total Test Cases**: 8
**Estimated Time**: 45-90 minutes
