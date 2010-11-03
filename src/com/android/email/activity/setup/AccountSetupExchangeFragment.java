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

package com.android.email.activity.setup;

import com.android.email.AccountBackupRestore;
import com.android.email.Email;
import com.android.email.ExchangeUtils;
import com.android.email.R;
import com.android.email.Utility;
import com.android.email.provider.EmailContent.Account;
import com.android.email.provider.EmailContent.HostAuth;
import com.android.exchange.ExchangeService;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.RemoteException;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.TextView;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Provides generic setup for Exchange accounts.
 *
 * This fragment is used by AccountSetupExchange (for creating accounts) and by AccountSettingsXL
 * (for editing existing accounts).
 */
public class AccountSetupExchangeFragment extends AccountServerBaseFragment
        implements OnCheckedChangeListener {

    private final static String STATE_KEY_CREDENTIAL =
        "AccountSetupExchangeFragment.loginCredential";

    private EditText mUsernameView;
    private EditText mPasswordView;
    private EditText mServerView;
    private CheckBox mSslSecurityView;
    private CheckBox mTrustCertificatesView;

    // Support for lifecycle
    private boolean mStarted;
    private boolean mLoaded;
    private String mCacheLoginCredential;

    /**
     * Called to do initial creation of a fragment.  This is called after
     * {@link #onAttach(Activity)} and before {@link #onActivityCreated(Bundle)}.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        if (Email.DEBUG_LIFECYCLE && Email.DEBUG) {
            Log.d(Email.LOG_TAG, "AccountSetupExchangeFragment onCreate");
        }
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            mCacheLoginCredential = savedInstanceState.getString(STATE_KEY_CREDENTIAL);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        if (Email.DEBUG_LIFECYCLE && Email.DEBUG) {
            Log.d(Email.LOG_TAG, "AccountSetupExchangeFragment onCreateView");
        }
        View view = inflater.inflate(R.layout.account_setup_exchange_fragment, container, false);
        Context context = getActivity();

        mUsernameView = (EditText) view.findViewById(R.id.account_username);
        mPasswordView = (EditText) view.findViewById(R.id.account_password);
        mServerView = (EditText) view.findViewById(R.id.account_server);
        mSslSecurityView = (CheckBox) view.findViewById(R.id.account_ssl);
        mSslSecurityView.setOnCheckedChangeListener(this);
        mTrustCertificatesView = (CheckBox) view.findViewById(R.id.account_trust_certificates);

        // Calls validateFields() which enables or disables the Next button
        // based on the fields' validity.
        TextWatcher validationTextWatcher = new TextWatcher() {
            public void afterTextChanged(Editable s) {
                validateFields();
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) { }
        };
        mUsernameView.addTextChangedListener(validationTextWatcher);
        mPasswordView.addTextChangedListener(validationTextWatcher);
        mServerView.addTextChangedListener(validationTextWatcher);

        //EXCHANGE-REMOVE-SECTION-START
        // Show device ID
        try {
            String deviceId = ExchangeService.getDeviceId(context);
            ((TextView) view.findViewById(R.id.device_id)).setText(deviceId);
        } catch (IOException ignore) {
            // There's nothing we can do here...
        }
        //EXCHANGE-REMOVE-SECTION-END

        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        if (Email.DEBUG_LIFECYCLE && Email.DEBUG) {
            Log.d(Email.LOG_TAG, "AccountSetupExchangeFragment onActivityCreated");
        }
        super.onActivityCreated(savedInstanceState);
    }

    /**
     * Called when the Fragment is visible to the user.
     */
    @Override
    public void onStart() {
        if (Email.DEBUG_LIFECYCLE && Email.DEBUG) {
            Log.d(Email.LOG_TAG, "AccountSetupExchangeFragment onStart");
        }
        super.onStart();
        mStarted = true;
        if (!mLoaded) {
            loadSettings(SetupData.getAccount());
        }
    }

    /**
     * Called when the fragment is visible to the user and actively running.
     */
    @Override
    public void onResume() {
        if (Email.DEBUG_LIFECYCLE && Email.DEBUG) {
            Log.d(Email.LOG_TAG, "AccountSetupExchangeFragment onResume");
        }
        super.onResume();
        validateFields();
    }

    @Override
    public void onPause() {
        if (Email.DEBUG_LIFECYCLE && Email.DEBUG) {
            Log.d(Email.LOG_TAG, "AccountSetupExchangeFragment onPause");
        }
        super.onPause();
    }

    /**
     * Called when the Fragment is no longer started.
     */
    @Override
    public void onStop() {
        if (Email.DEBUG_LIFECYCLE && Email.DEBUG) {
            Log.d(Email.LOG_TAG, "AccountSetupExchangeFragment onStop");
        }
        super.onStop();
        mStarted = false;
    }

    /**
     * Called when the fragment is no longer in use.
     */
    @Override
    public void onDestroy() {
        if (Email.DEBUG_LIFECYCLE && Email.DEBUG) {
            Log.d(Email.LOG_TAG, "AccountSetupExchangeFragment onDestroy");
        }
        super.onDestroy();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        if (Email.DEBUG_LIFECYCLE && Email.DEBUG) {
            Log.d(Email.LOG_TAG, "AccountSetupExchangeFragment onSaveInstanceState");
        }
        super.onSaveInstanceState(outState);

        outState.putString(STATE_KEY_CREDENTIAL, mCacheLoginCredential);
    }

    /**
     * Activity provides callbacks here.  This also triggers loading and setting up the UX
     */
    @Override
    public void setCallback(Callback callback) {
        super.setCallback(callback);
        if (mStarted && !mLoaded) {
            loadSettings(SetupData.getAccount());
        }
    }

    /**
     * Load the current settings into the UI
     *
     * @return true if the loaded values pass validation
     */
    /* package */ boolean loadSettings(Account account) {
        HostAuth hostAuth = account.mHostAuthRecv;

        String userName = hostAuth.mLogin;
        if (userName != null) {
            // Add a backslash to the start of the username, but only if the username has no
            // backslash in it.
            if (userName.indexOf('\\') < 0) {
                userName = "\\" + userName;
            }
            mUsernameView.setText(userName);
        }

        if (hostAuth.mPassword != null) {
            mPasswordView.setText(hostAuth.mPassword);
        }

        String protocol = hostAuth.mProtocol;
        if (protocol == null || !protocol.startsWith("eas")) {
            throw new Error("Unknown account type: " + account.getStoreUri(mContext));
        }

        if (hostAuth.mAddress != null) {
            mServerView.setText(hostAuth.mAddress);
        }

        boolean ssl = 0 != (hostAuth.mFlags & HostAuth.FLAG_SSL);
        boolean trustCertificates = 0 != (hostAuth.mFlags & HostAuth.FLAG_TRUST_ALL_CERTIFICATES);
        mSslSecurityView.setChecked(ssl);
        mTrustCertificatesView.setChecked(trustCertificates);
        mTrustCertificatesView.setVisibility(ssl ? View.VISIBLE : View.GONE);

        return validateFields();
    }

    private boolean usernameFieldValid(EditText usernameView) {
        return Utility.isTextViewNotEmpty(usernameView) &&
            !usernameView.getText().toString().equals("\\");
    }

    /**
     * Check the values in the fields and decide if it makes sense to enable the "next" button
     * @return true if all fields are valid, false if any fields are incomplete
     */
    private boolean validateFields() {
        boolean enabled = usernameFieldValid(mUsernameView)
                && Utility.isTextViewNotEmpty(mPasswordView)
                && Utility.isTextViewNotEmpty(mServerView);
        if (enabled) {
            try {
                URI uri = getUri();
            } catch (URISyntaxException use) {
                enabled = false;
            }
        }
        enableNextButton(enabled);
        return enabled;
    }

    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (buttonView.getId() == R.id.account_ssl) {
            mTrustCertificatesView.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        }
    }
    /**
     * Entry point from Activity after editing settings and verifying them.  Must be FLOW_MODE_EDIT.
     *
     * TODO: Was the !isSaved() logic ever actually used?
     */
    @Override
    public void saveSettingsAfterEdit() {
        Account account = SetupData.getAccount();
        if (account.isSaved()) {
            // Account.update will NOT save the HostAuth's
            account.update(mContext, account.toContentValues());
            account.mHostAuthRecv.update(mContext, account.mHostAuthRecv.toContentValues());
            account.mHostAuthSend.update(mContext, account.mHostAuthSend.toContentValues());
            if (account.mHostAuthRecv.mProtocol.equals("eas")) {
                // For EAS, notify ExchangeService that the password has changed
                try {
                    ExchangeUtils.getExchangeService(mContext, null).hostChanged(account.mId);
                } catch (RemoteException e) {
                    // Nothing to be done if this fails
                }
            }
        } else {
            // Account.save will save the HostAuth's
            account.save(mContext);
        }
        // Update the backup (side copy) of the accounts
        AccountBackupRestore.backupAccounts(mContext);
    }

    /**
     * This entry point is not used (unlike in AccountSetupIncomingFragment) because the data
     * is already saved by onNext().
     * TODO: Reconcile this, to make them more consistent.
     */
    @Override
    public void saveSettingsAfterSetup() {
    }

    /**
     * Entry point from Activity after entering new settings and verifying them.  For setup mode.
     */
    public boolean setHostAuthFromAutodiscover(HostAuth newHostAuth) {
        Account account = SetupData.getAccount();
        account.mHostAuthSend = newHostAuth;
        account.mHostAuthRecv = newHostAuth;
        return loadSettings(account);
    }

    /**
     * Attempt to create a URI from the fields provided.  Throws URISyntaxException if there's
     * a problem with the user input.
     * @return a URI built from the account setup fields
     */
    /* package */ URI getUri() throws URISyntaxException {
        boolean sslRequired = mSslSecurityView.isChecked();
        boolean trustCertificates = mTrustCertificatesView.isChecked();
        String scheme = (sslRequired)
                        ? (trustCertificates ? "eas+ssl+trustallcerts" : "eas+ssl+")
                        : "eas";
        String userName = mUsernameView.getText().toString().trim();
        // Remove a leading backslash, if there is one, since we now automatically put one at
        // the start of the username field
        if (userName.startsWith("\\")) {
            userName = userName.substring(1);
        }
        mCacheLoginCredential = userName;
        String userInfo = userName + ":" + mPasswordView.getText();
        String host = mServerView.getText().toString().trim();
        String path = null;

        URI uri = new URI(
                scheme,
                userInfo,
                host,
                0,
                path,
                null,
                null);

        return uri;
    }

    /**
     * Implements AccountCheckSettingsFragment.Callbacks
     */
    @Override
    public void onAutoDiscoverComplete(int result, HostAuth hostAuth) {
        AccountSetupExchange activity = (AccountSetupExchange) getActivity();
        activity.onAutoDiscoverComplete(result, hostAuth);
    }

    /**
     * Entry point from Activity, when "next" button is clicked
     */
    @Override
    public void onNext() {
        try {
            URI uri = getUri();
            Account setupAccount = SetupData.getAccount();
            setupAccount.setStoreUri(mContext, uri.toString());
            setupAccount.setSenderUri(mContext, uri.toString());

            // Stop here if the login credentials duplicate an existing account
            // (unless they duplicate the existing account, as they of course will)
            Account account = Utility.findExistingAccount(mContext, setupAccount.mId,
                    uri.getHost(), mCacheLoginCredential);
            if (account != null) {
                DuplicateAccountDialogFragment dialogFragment =
                    DuplicateAccountDialogFragment.newInstance(account.mDisplayName);
                dialogFragment.show(getActivity(), DuplicateAccountDialogFragment.TAG);
                return;
            }
        } catch (URISyntaxException use) {
            /*
             * It's unrecoverable if we cannot create a URI from components that
             * we validated to be safe.
             */
            throw new Error(use);
        }

        mCallback.onProceedNext(SetupData.CHECK_INCOMING, this);
    }
}