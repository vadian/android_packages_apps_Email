/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.email.service;

import com.android.email.AccountTestCase;
import com.android.email.Controller;
import com.android.email.Email;
import com.android.email.Utility;
import com.android.email.provider.EmailContent;
import com.android.email.provider.EmailProvider;
import com.android.email.provider.ProviderTestUtils;
import com.android.email.provider.EmailContent.Account;
import com.android.email.provider.EmailContent.HostAuth;
import com.android.email.service.MailService.AccountSyncReport;

import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;

import java.util.HashMap;

/**
 * Tests of the Email provider.
 *
 * You can run this entire test case with:
 *   runtest -c com.android.email.service.MailServiceTests email
 */
public class MailServiceTests extends AccountTestCase {

    EmailProvider mProvider;
    Context mMockContext;

    public MailServiceTests() {
        super();
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mMockContext = getMockContext();
        // Delete any test accounts we might have created earlier
        deleteTemporaryAccountManagerAccounts();
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        // Delete any test accounts we might have created earlier
        deleteTemporaryAccountManagerAccounts();
    }

    /**
     * Confirm that the test below is functional (and non-destructive) when there are
     * prexisting (non-test) accounts in the account manager.
     */
    public void testTestReconcileAccounts() {
        Account firstAccount = null;
        final String TEST_USER_ACCOUNT = "__user_account_test_1";
        Context context = getContext();
        try {
            // Note:  Unlike calls to setupProviderAndAccountManagerAccount(), we are creating
            // *real* accounts here (not in the mock provider)
            createAccountManagerAccount(TEST_USER_ACCOUNT + TEST_ACCOUNT_SUFFIX);
            firstAccount = ProviderTestUtils.setupAccount(TEST_USER_ACCOUNT, true, context);
            // Now run the test with the "user" accounts in place
            testReconcileAccounts();
        } finally {
            if (firstAccount != null) {
                boolean firstAccountFound = false;
                // delete the provider account
                context.getContentResolver().delete(firstAccount.getUri(), null, null);
                // delete the account manager account
                android.accounts.Account[] accountManagerAccounts = AccountManager.get(context)
                        .getAccountsByType(Email.EXCHANGE_ACCOUNT_MANAGER_TYPE);
                for (android.accounts.Account accountManagerAccount: accountManagerAccounts) {
                    if ((TEST_USER_ACCOUNT + TEST_ACCOUNT_SUFFIX)
                            .equals(accountManagerAccount.name)) {
                        deleteAccountManagerAccount(accountManagerAccount);
                        firstAccountFound = true;
                    }
                }
                assertTrue(firstAccountFound);
            }
        }
    }

    /**
     * Call onAccountsUpdated as fast as we can from a single thread (simulating them all coming
     * from the UI thread); it shouldn't crash
     */
    public void testThrashOnAccountsUpdated() {
        final MailService mailService = MailService.getMailServiceForTest();
        assertNotNull(mailService);
        final android.accounts.Account[] accounts = getExchangeAccounts();
        Utility.runAsync(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < 1000; i++) {
                    mailService.mAccountsUpdatedListener.onAccountsUpdated(accounts);
                }
            }});
    }

    /**
     * Note, there is some inherent risk in this test, as it creates *real* accounts in the
     * system (it cannot use the mock context with the Account Manager).
     */
    public void testReconcileAccounts() {
        // Note that we can't use mMockContext for AccountManager interactions, as it isn't a fully
        // functional Context.
        Context context = getContext();

        // Capture the baseline (account manager accounts) so we can measure the changes
        // we're making, irrespective of the number of actual accounts, and not destroy them
        android.accounts.Account[] baselineAccounts =
            AccountManager.get(context).getAccountsByType(Email.EXCHANGE_ACCOUNT_MANAGER_TYPE);

        // Set up three accounts, both in AccountManager and in EmailProvider
        Account firstAccount = setupProviderAndAccountManagerAccount(getTestAccountName("1"));
        setupProviderAndAccountManagerAccount(getTestAccountName("2"));
        setupProviderAndAccountManagerAccount(getTestAccountName("3"));

        // Check that they're set up properly
        assertEquals(3, EmailContent.count(mMockContext, Account.CONTENT_URI, null, null));
        android.accounts.Account[] accountManagerAccounts =
                getAccountManagerAccounts(baselineAccounts);
        assertEquals(3, accountManagerAccounts.length);

        // Delete account "2" from AccountManager
        android.accounts.Account removedAccount =
            makeAccountManagerAccount(getTestAccountEmailAddress("2"));
        deleteAccountManagerAccount(removedAccount);

        // Confirm it's deleted
        accountManagerAccounts = getAccountManagerAccounts(baselineAccounts);
        assertEquals(2, accountManagerAccounts.length);

        // Run the reconciler
        ContentResolver resolver = mMockContext.getContentResolver();
        MailService.reconcileAccountsWithAccountManager(context,
                makeExchangeServiceAccountList(), accountManagerAccounts, true, resolver);

        // There should now be only two EmailProvider accounts
        assertEquals(2, EmailContent.count(mMockContext, Account.CONTENT_URI, null, null));

        // Ok, now we've got two of each; let's delete a provider account
        resolver.delete(ContentUris.withAppendedId(Account.CONTENT_URI, firstAccount.mId),
                null, null);
        // ...and then there was one
        assertEquals(1, EmailContent.count(mMockContext, Account.CONTENT_URI, null, null));

        // Run the reconciler
        MailService.reconcileAccountsWithAccountManager(context,
                makeExchangeServiceAccountList(), accountManagerAccounts, true, resolver);

        // There should now be only one AccountManager account
        accountManagerAccounts = getAccountManagerAccounts(baselineAccounts);
        assertEquals(1, accountManagerAccounts.length);
        // ... and it should be account "3"
        assertEquals(getTestAccountEmailAddress("3"), accountManagerAccounts[0].name);
    }
    /**
     * Lightweight subclass of the Controller class allows injection of mock context
     */
    public static class TestController extends Controller {

        protected TestController(Context providerContext, Context systemContext) {
            super(systemContext);
            setProviderContext(providerContext);
        }
    }

    /**
     * Create a simple HostAuth with protocol
     */
    private HostAuth setupSimpleHostAuth(String protocol) {
        HostAuth hostAuth = new HostAuth();
        hostAuth.mProtocol = protocol;
        return hostAuth;
    }

    /**
     * Initial testing on setupSyncReportsLocked, making sure that EAS accounts aren't scheduled
     */
    public void testSetupSyncReportsLocked() {
        // TODO Test other functionality within setupSyncReportsLocked
        // Setup accounts of each type, all with manual sync at different intervals
        Account easAccount = ProviderTestUtils.setupAccount("account1", false, mMockContext);
        easAccount.mHostAuthRecv = setupSimpleHostAuth("eas");
        easAccount.mSyncInterval = 30;
        easAccount.save(mMockContext);
        Account imapAccount = ProviderTestUtils.setupAccount("account2", false, mMockContext);
        imapAccount.mHostAuthRecv = setupSimpleHostAuth("imap");
        imapAccount.mSyncInterval = 60;
        imapAccount.save(mMockContext);
        Account pop3Account = ProviderTestUtils.setupAccount("account3", false, mMockContext);
        pop3Account.mHostAuthRecv = setupSimpleHostAuth("pop3");
        pop3Account.mSyncInterval = 90;
        pop3Account.save(mMockContext);

        // Setup the SyncReport's for these Accounts
        MailService mailService = new MailService();
        mailService.mController = new TestController(mMockContext, getContext());
        try {
            mailService.setupSyncReportsLocked(MailService.SYNC_REPORTS_RESET, mMockContext);

            // Get back the map created by MailService
            HashMap<Long, AccountSyncReport> syncReportMap = MailService.mSyncReports;
            synchronized (syncReportMap) {
                // Check the SyncReport's for correctness of sync interval
                AccountSyncReport syncReport = syncReportMap.get(easAccount.mId);
                assertNotNull(syncReport);
                // EAS sync interval should have been changed to "never"
                assertEquals(Account.CHECK_INTERVAL_NEVER, syncReport.syncInterval);
                syncReport = syncReportMap.get(imapAccount.mId);
                assertNotNull(syncReport);
                assertEquals(60, syncReport.syncInterval);
                syncReport = syncReportMap.get(pop3Account.mId);
                assertNotNull(syncReport);
                assertEquals(90, syncReport.syncInterval);
                // Change the EAS account to push
                ContentValues cv = new ContentValues();
                cv.put(Account.SYNC_INTERVAL, Account.CHECK_INTERVAL_PUSH);
                easAccount.update(mMockContext, cv);
                syncReportMap.clear();
                mailService.setupSyncReportsLocked(easAccount.mId, mMockContext);
                syncReport = syncReportMap.get(easAccount.mId);
                assertNotNull(syncReport);
                // EAS sync interval should be "never" in this case as well
                assertEquals(Account.CHECK_INTERVAL_NEVER, syncReport.syncInterval);
            }
        } finally {
            mailService.mController.cleanupForTest();
        }
    }
}