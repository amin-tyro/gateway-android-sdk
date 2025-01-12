package com.mastercard.gateway.android.sampleapp;

import android.app.Activity;
import android.content.Intent;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.support.annotation.DrawableRes;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;

import com.mastercard.gateway.android.sampleapp.databinding.ActivityProcessPaymentBinding;
import com.mastercard.gateway.android.sdk.Gateway;
import com.mastercard.gateway.android.sdk.GatewayCallback;
import com.mastercard.gateway.android.sdk.GatewayMap;

import java.util.UUID;

public class ProcessPaymentActivity extends AppCompatActivity {

    static final int REQUEST_CARD_INFO = 100;

    // static for demo
    static final String AMOUNT = "1.00";
    static final String CURRENCY = "AUD";

    ActivityProcessPaymentBinding binding;
    Gateway gateway;
    String sessionId, apiVersion, orderId, transactionId;
    // leave this false, since Google pay is not supported yet
    boolean isGooglePay = false;
    ApiController apiController = ApiController.getInstance();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_process_payment);

        // init api controller
        apiController.setMerchantServerUrl(Config.MERCHANT_URL.getValue(this));

        // init gateway
        gateway = new Gateway();
        gateway.setMerchantId(Config.MERCHANT_ID.getValue(this));
        try {
            Gateway.Region region = Gateway.Region.valueOf(Config.REGION.getValue(this));
            gateway.setRegion(region);
        } catch (Exception e) {
            Log.e(ProcessPaymentActivity.class.getSimpleName(), "Invalid Gateway region value provided", e);
        }

        // random order/txn IDs for example purposes
        orderId = UUID.randomUUID().toString();
        orderId = orderId.substring(0, orderId.indexOf('-'));
        transactionId = UUID.randomUUID().toString();
        transactionId = transactionId.substring(0, transactionId.indexOf('-'));

        // bind buttons
        binding.startButton.setOnClickListener(v -> createSession());
        binding.confirmButton.setOnClickListener(v -> {
            processPayment();
        });
        binding.doneButton.setOnClickListener(v -> finish());

        initUI();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CARD_INFO) {
            binding.collectCardInfoProgress.setVisibility(View.GONE);

            if (resultCode == Activity.RESULT_OK) {
                binding.collectCardInfoSuccess.setVisibility(View.VISIBLE);

                String googlePayToken = data.getStringExtra(CollectCardInfoActivity.EXTRA_PAYMENT_TOKEN);

                String cardDescription = data.getStringExtra(CollectCardInfoActivity.EXTRA_CARD_DESCRIPTION);
                binding.confirmCardDescription.setText(cardDescription);

                if (googlePayToken != null) {
                    isGooglePay = true;

                    String paymentToken = data.getStringExtra(CollectCardInfoActivity.EXTRA_PAYMENT_TOKEN);

                    updateSession(paymentToken);
                } else {
                    isGooglePay = false;

                    String cardName = data.getStringExtra(CollectCardInfoActivity.EXTRA_CARD_NAME);
                    String cardNumber = data.getStringExtra(CollectCardInfoActivity.EXTRA_CARD_NUMBER);
                    String cardExpiryMonth = data.getStringExtra(CollectCardInfoActivity.EXTRA_CARD_EXPIRY_MONTH);
                    String cardExpiryYear = data.getStringExtra(CollectCardInfoActivity.EXTRA_CARD_EXPIRY_YEAR);
                    String cardCvv = data.getStringExtra(CollectCardInfoActivity.EXTRA_CARD_CVV);

                    updateSession(cardName, cardNumber, cardExpiryMonth, cardExpiryYear, cardCvv);
                }

            } else {
                binding.collectCardInfoError.setVisibility(View.VISIBLE);

                showResult(R.drawable.failed, R.string.pay_error_card_info_not_collected);
            }

            return;
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    void initUI() {
        binding.createSessionProgress.setVisibility(View.GONE);
        binding.createSessionSuccess.setVisibility(View.GONE);
        binding.createSessionError.setVisibility(View.GONE);

        binding.collectCardInfoProgress.setVisibility(View.GONE);
        binding.collectCardInfoSuccess.setVisibility(View.GONE);
        binding.collectCardInfoError.setVisibility(View.GONE);

        binding.updateSessionProgress.setVisibility(View.GONE);
        binding.updateSessionSuccess.setVisibility(View.GONE);
        binding.updateSessionError.setVisibility(View.GONE);

        binding.processPaymentProgress.setVisibility(View.GONE);
        binding.processPaymentSuccess.setVisibility(View.GONE);
        binding.processPaymentError.setVisibility(View.GONE);

        binding.startButton.setEnabled(true);
        binding.confirmButton.setEnabled(true);

        binding.startButton.setVisibility(View.VISIBLE);
        binding.groupConfirm.setVisibility(View.GONE);
        binding.groupResult.setVisibility(View.GONE);
    }

    void createSession() {
        binding.startButton.setEnabled(false);
        binding.createSessionProgress.setVisibility(View.VISIBLE);

        apiController.createSession(new CreateSessionCallback());
    }

    void collectCardInfo() {
        binding.collectCardInfoProgress.setVisibility(View.VISIBLE);

        Intent i = new Intent(this, CollectCardInfoActivity.class);
        i.putExtra(CollectCardInfoActivity.EXTRA_GOOGLE_PAY_TXN_AMOUNT, AMOUNT);
        i.putExtra(CollectCardInfoActivity.EXTRA_GOOGLE_PAY_TXN_CURRENCY, CURRENCY);

        startActivityForResult(i, REQUEST_CARD_INFO);
    }

    void updateSession(String paymentToken) {
        binding.updateSessionProgress.setVisibility(View.VISIBLE);

        GatewayMap request = new GatewayMap()
                .set("sourceOfFunds.provided.card.devicePayment.paymentToken", paymentToken);

        gateway.updateSession(sessionId, apiVersion, request, new UpdateSessionCallback());
    }

    void updateSession(String name, String number, String expiryMonth, String expiryYear, String cvv) {
        binding.updateSessionProgress.setVisibility(View.VISIBLE);

        // build the gateway request
        GatewayMap request = new GatewayMap()
                .set("sourceOfFunds.provided.card.nameOnCard", name)
                .set("sourceOfFunds.provided.card.number", number)
                .set("sourceOfFunds.provided.card.securityCode", cvv)
                .set("sourceOfFunds.provided.card.expiry.month", expiryMonth)
                .set("sourceOfFunds.provided.card.expiry.year", expiryYear);

        gateway.updateSession(sessionId, apiVersion, request, new UpdateSessionCallback());
    }

    void processPayment() {
        binding.processPaymentProgress.setVisibility(View.VISIBLE);

        apiController.completeSession(sessionId, orderId, transactionId, AMOUNT, CURRENCY, isGooglePay, new CompleteSessionCallback());
    }

    void showResult(@DrawableRes int iconId, @StringRes int messageId) {
        binding.resultIcon.setImageResource(iconId);
        binding.resultText.setText(messageId);

        binding.groupConfirm.setVisibility(View.GONE);
        binding.groupResult.setVisibility(View.VISIBLE);
    }


    class CreateSessionCallback implements ApiController.CreateSessionCallback {
        @Override
        public void onSuccess(String sessionId, String apiVersion) {
            Log.i("CreateSessionTask", "Session established");
            binding.createSessionProgress.setVisibility(View.GONE);
            binding.createSessionSuccess.setVisibility(View.VISIBLE);

            ProcessPaymentActivity.this.sessionId = sessionId;
            ProcessPaymentActivity.this.apiVersion = apiVersion;

            collectCardInfo();
        }

        @Override
        public void onError(Throwable throwable) {
            Log.e(ProcessPaymentActivity.class.getSimpleName(), throwable.getMessage(), throwable);

            binding.createSessionProgress.setVisibility(View.GONE);
            binding.createSessionError.setVisibility(View.VISIBLE);

            showResult(R.drawable.failed, R.string.pay_error_unable_to_create_session);
        }
    }

    class UpdateSessionCallback implements GatewayCallback {
        @Override
        public void onSuccess(GatewayMap response) {
            Log.i(ProcessPaymentActivity.class.getSimpleName(), "Successfully updated session");
            binding.updateSessionProgress.setVisibility(View.GONE);
            binding.updateSessionSuccess.setVisibility(View.VISIBLE);

            binding.startButton.setVisibility(View.GONE);
            binding.groupConfirm.setVisibility(View.VISIBLE);
        }

        @Override
        public void onError(Throwable throwable) {
            Log.e(ProcessPaymentActivity.class.getSimpleName(), throwable.getMessage(), throwable);

            binding.updateSessionProgress.setVisibility(View.GONE);
            binding.updateSessionError.setVisibility(View.VISIBLE);

            showResult(R.drawable.failed, R.string.pay_error_unable_to_update_session);
        }
    }

    class CompleteSessionCallback implements ApiController.CompleteSessionCallback {
        @Override
        public void onSuccess(String result) {
            binding.processPaymentProgress.setVisibility(View.GONE);
            binding.processPaymentSuccess.setVisibility(View.VISIBLE);

            showResult(R.drawable.success, R.string.pay_you_payment_was_successful);
        }

        @Override
        public void onError(Throwable throwable) {
            Log.e(ProcessPaymentActivity.class.getSimpleName(), throwable.getMessage(), throwable);

            binding.processPaymentProgress.setVisibility(View.GONE);
            binding.processPaymentError.setVisibility(View.VISIBLE);

            showResult(R.drawable.failed, R.string.pay_error_processing_your_payment);
        }
    }
}
