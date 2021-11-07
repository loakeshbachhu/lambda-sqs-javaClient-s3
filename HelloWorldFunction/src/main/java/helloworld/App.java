package helloworld;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.amazon.sqs.javamessaging.AmazonSQSExtendedClient;
import com.amazon.sqs.javamessaging.ExtendedClientConfiguration;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.*;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.*;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Handler for requests to Lambda function.
 */
public class App {
    // Create an Amazon S3 bucket with a random name.
    private final static String S3_BUCKET_NAME = UUID.randomUUID() + "-"
            + DateTimeFormat.forPattern("yyMMdd-hhmmss").print(new DateTime());
    public void publishMessageToSqs() {

        /*
         * Create a new instance of the builder with all defaults (credentials
         * and region) set automatically. For more information, see
         * Creating Service Clients in the AWS SDK for Java Developer Guide.
         */
        final AmazonS3 s3 = AmazonS3ClientBuilder.defaultClient();

        /*
         * Set the Amazon S3 bucket name, and then set a lifecycle rule on the
         * bucket to permanently delete objects 14 days after each object's
         * creation date.
         */
        final BucketLifecycleConfiguration.Rule expirationRule =
                new BucketLifecycleConfiguration.Rule();
        expirationRule.withExpirationInDays(14).withStatus("Enabled");
        final BucketLifecycleConfiguration lifecycleConfig =
                new BucketLifecycleConfiguration().withRules(expirationRule);

        // Create the bucket and allow message objects to be stored in the bucket.
        s3.createBucket(S3_BUCKET_NAME);
        s3.setBucketLifecycleConfiguration(S3_BUCKET_NAME, lifecycleConfig);
        System.out.println("Bucket created and configured.");

        /*
         * Set the Amazon SQS extended client configuration with large payload
         * support enabled.
         */
        final ExtendedClientConfiguration extendedClientConfig =
                new ExtendedClientConfiguration()
                        .withLargePayloadSupportEnabled(s3, S3_BUCKET_NAME);


        final AmazonSQS sqsExtended =
                new AmazonSQSExtendedClient(AmazonSQSClientBuilder
                        .defaultClient(), extendedClientConfig);

        /*
         * Create a long string of characters for the message object which will
         * be stored in the bucket.
         */
        int stringLength = 300000;
        char[] chars = new char[stringLength];
        Arrays.fill(chars, 'x');
        final String myLongString = new String(chars);

        // Create a message queue for this example.
//        final String QueueName = "MyQueue" + UUID.randomUUID().toString();
//        final CreateQueueRequest createQueueRequest =
//                new CreateQueueRequest(QueueName);
//        final String myQueueUrl = sqsExtended
//                .createQueue(createQueueRequest).getQueueUrl();
//        System.out.println("Queue created.");

        // Send the message.
        final SendMessageRequest myMessageRequest =
                new SendMessageRequest("https://sqs.us-east-2.amazonaws.com/121279407446/lambda_Sqs_Large_Objects", myLongString);
        sqsExtended.sendMessage(myMessageRequest);
        System.out.println("Sent the message.");

        // Receive the message.
        final ReceiveMessageRequest receiveMessageRequest =
                new ReceiveMessageRequest("https://sqs.us-east-2.amazonaws.com/121279407446/lambda_Sqs_Large_Objects");
        List<Message> messages = sqsExtended
                .receiveMessage(receiveMessageRequest).getMessages();

        // Print information about the message.
        for (Message message : messages) {
            System.out.println("\nMessage received.");
            System.out.println("  ID: " + message.getMessageId());
            System.out.println("  Receipt handle: " + message.getReceiptHandle());
            System.out.println("  Message body (first 5 characters): "
                    + message.getBody().substring(0, 5));
        }

        // Delete the message, the queue, and the bucket.
//        final String messageReceiptHandle = messages.get(0).getReceiptHandle();
//        sqsExtended.deleteMessage(new DeleteMessageRequest(myQueueUrl,
//                messageReceiptHandle));
//        System.out.println("Deleted the message.");
//
//        sqsExtended.deleteQueue(new DeleteQueueRequest(myQueueUrl));
//        System.out.println("Deleted the queue.");
//
//        //deleteBucketAndAllContents(s3);
//        System.out.println("Deleted the bucket.");

    }

    private static void deleteBucketAndAllContents(AmazonS3 client) {

        ObjectListing objectListing = client.listObjects(S3_BUCKET_NAME);

        while (true) {
            for (S3ObjectSummary objectSummary : objectListing
                    .getObjectSummaries()) {
                client.deleteObject(S3_BUCKET_NAME, objectSummary.getKey());
            }

            if (objectListing.isTruncated()) {
                objectListing = client.listNextBatchOfObjects(objectListing);
            } else {
                break;
            }
        }

        final VersionListing list = client.listVersions(
                new ListVersionsRequest().withBucketName(S3_BUCKET_NAME));

        for (S3VersionSummary s : list.getVersionSummaries()) {
            client.deleteVersion(S3_BUCKET_NAME, s.getKey(), s.getVersionId());
        }

        client.deleteBucket(S3_BUCKET_NAME);
    }
}
