package com.atlassian.bitbucket.jenkins.internal.jenkins.oauth.consumer;

import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.consumer.Consumer;
import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.consumer.Consumer.Builder;
import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.consumer.ServiceProviderConsumerStore;
import com.atlassian.bitbucket.jenkins.internal.provider.JenkinsProvider;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.inject.Inject;
import javax.servlet.ServletException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;

import static com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.consumer.Consumer.SignatureMethod.HMAC_SHA1;
import static com.atlassian.bitbucket.jenkins.internal.util.FormValidationUtils.checkBaseUrl;
import static org.apache.commons.lang3.StringUtils.isAlphanumeric;
import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * A wrapper class exposing getter functions from an OAuthConsumer to the UI
 */
public class OAuthConsumerEntry extends AbstractDescribableImpl<OAuthConsumerEntry> {

    /**
     * Because of JENKINS-26579, we can't use these in Jelly files. Since during create consumer, there is
     * no consumer yet.
     */
    private static String CONSUMER_KEY_FIELD = "consumerKey";
    private static String CONSUMER_NAME_FIELD = "consumerName";
    private static String CONSUMER_SECRET_FIELD = "consumerSecret";
    private static String CONSUMER_CALLBACKURL_FIELD = "callbackUrl";

    private static final OAuthConsumerEntry BLANK_ENTRY =
            new OAuthConsumerEntry(
                    Consumer.key("Enter Key")
                            .name("Enter Name")
                            .signatureMethod(HMAC_SHA1)
                            .build(), false);

    private final Consumer consumer;
    private final boolean isUpdate;

    public OAuthConsumerEntry(Consumer consumer, boolean isUpdate) {
        this.consumer = consumer;
        this.isUpdate = isUpdate;
    }

    public static OAuthConsumerEntry getOAuthConsumerForAdd() {
        return BLANK_ENTRY;
    }

    public static OAuthConsumerEntry getOAuthConsumerForUpdate(Consumer consumer) {
        return new OAuthConsumerEntry(consumer, true);
    }

    public String getCallbackUrl() {
        return isCallbackUrlSet() ? consumer.getCallback().get().toString() : "";
    }

    public boolean isCallbackUrlSet() {
        return consumer.getCallback().isPresent();
    }

    public String getConsumerKey() {
        return consumer.getKey();
    }

    public String getConsumerName() {
        return consumer.getName();
    }

    @SuppressWarnings("unused")
    public String getConsumerSecret() {
        return consumer.getConsumerSecret().orElse("");
    }

    public boolean isUpdate() {
        return isUpdate;
    }

    public OAuthConsumerEntryDescriptor getDescriptor() {
        return (OAuthConsumerEntryDescriptor) super.getDescriptor();
    }

    @Extension
    public static class OAuthConsumerEntryDescriptor extends Descriptor<OAuthConsumerEntry> {
        
        @Inject
        private JenkinsProvider jenkinsProvider;
        @Inject
        private ServiceProviderConsumerStore consumerStore;

        public FormValidation doCheckConsumerKey(@QueryParameter String consumerKey) {
            jenkinsProvider.get().checkPermission(Jenkins.ADMINISTER);
            String k = consumerKey.replaceAll("-", "");
            if (!isAlphanumeric(k)) {
                return FormValidation.error("Consumer key cannot be empty");
            } else if (consumerStore.get(consumerKey).isPresent()) {
                return FormValidation.error("Key with the same name already exists");
            } else {
                return FormValidation.ok();
            }
        }

        public FormValidation doCheckConsumerName(@QueryParameter String consumerName) {
            jenkinsProvider.get().checkPermission(Jenkins.ADMINISTER);
            if (isBlank(consumerName)) {
                return FormValidation.error("Consumer name cannot be empty");
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckCallbackUrl(@QueryParameter String callbackUrl) {
            jenkinsProvider.get().checkPermission(Jenkins.ADMINISTER);
            return checkBaseUrl(callbackUrl);
        }

        public FormValidation doCheckConsumerSecret(@QueryParameter String consumerSecret) {
            jenkinsProvider.get().checkPermission(Jenkins.ADMINISTER);
            if (isBlank(consumerSecret)) {
                return FormValidation.error("Consumer secret cannot be empty");
            }
            return FormValidation.ok();
        }

        public Consumer getConsumerFromSubmittedForm(
                StaplerRequest request) throws ServletException, URISyntaxException, FormException {
            jenkinsProvider.get().checkPermission(Jenkins.ADMINISTER);
            JSONObject data = request.getSubmittedForm();
            String consumerKey = data.getString(CONSUMER_KEY_FIELD);
            String consumerName = data.getString(CONSUMER_NAME_FIELD);
            String consumerSecret = data.getString(CONSUMER_SECRET_FIELD);
            String callbackUrl = data.getString(CONSUMER_CALLBACKURL_FIELD);

            FormValidation formValidation = FormValidation.aggregate(Arrays.asList(
                    doCheckConsumerKey(consumerKey),
                    doCheckConsumerName(consumerName),
                    doCheckCallbackUrl(callbackUrl),
                    doCheckConsumerSecret(consumerSecret)));

            if (formValidation.kind == FormValidation.Kind.ERROR) {
                // The form field is unused, but the Exception constructor requires it- value here is arbitrary
                throw new FormException(Messages.bitbucket_oauth_consumer_entry_form_error() + "\n" + 
                                        formValidation.getMessage(), CONSUMER_KEY_FIELD);
            }

            Builder builder = Consumer.key(consumerKey)
                    .name(consumerName)
                    .consumerSecret(consumerSecret)
                    .signatureMethod(HMAC_SHA1);
            if (!isBlank(callbackUrl)) {
                builder.callback(new URI(callbackUrl));
            }
            return builder.build();
        }
    }
}
