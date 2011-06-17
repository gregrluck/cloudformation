package org.terracotta;/*
 * Copyright 2011 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

import java.util.ArrayList;
import java.util.List;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.PropertiesCredentials;
import com.amazonaws.services.cloudformation.AmazonCloudFormation;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClient;
import com.amazonaws.services.cloudformation.model.CreateStackRequest;
import com.amazonaws.services.cloudformation.model.DescribeStacksRequest;
import com.amazonaws.services.cloudformation.model.DescribeStackResourcesRequest;
import com.amazonaws.services.cloudformation.model.DeleteStackRequest;
import com.amazonaws.services.cloudformation.model.Parameter;
import com.amazonaws.services.cloudformation.model.Stack;
import com.amazonaws.services.cloudformation.model.StackStatus;
import com.amazonaws.services.cloudformation.model.StackResource;

/**
 * This sample demonstrates how to make basic requests to AWS CloudFormation
 * using the AWS SDK for Java.
 * <p/>
 * <b>Prerequisites:</b> You must have a valid Amazon Web Services developer
 * account, and be signed up to use AWS CloudFormation. For more information on
 * AWS CloudFormation, see http://aws.amazon.com/cloudformation.
 * <p/>
 * <b>Important:</b> Be sure to fill in your AWS access credentials in the
 * AwsCredentials.properties file before you try to run this
 * sample.
 * http://aws.amazon.com/security-credentials
 */
public class TerracottaCloudFormationSample {


    private static String stackName = "TerracottaCloudFormationSampleStack";
    private static String logicalResourceName = "SampleNotificationTopic";
    private static String keyChainName = "gluck"; //change this to the key chain you want to use to ssh into instances.
    private static AmazonCloudFormation amazonCloudFormationClient;

    @org.junit.BeforeClass
    public static void createStack() throws Exception {

        /*
        * Important: Be sure to fill in your AWS access credentials in the
        *            AwsCredentials.properties file before you try to run this
        *            sample.
        * http://aws.amazon.com/security-credentials
        */
        amazonCloudFormationClient = new AmazonCloudFormationClient(new PropertiesCredentials(
                TerracottaCloudFormationSample.class.getResourceAsStream("/AwsCredentials.properties")));


        System.out.println("================================");
        System.out.println("Terracotta CloudFormation Sample");
        System.out.println("================================\n");


        try {
            // Create a stack
            CreateStackRequest createRequest = new CreateStackRequest();
            createRequest.setStackName(stackName);
            createRequest.setTemplateBody(convertStreamToString(TerracottaCloudFormationSample.class.getResourceAsStream("/TerracottaServerArray.template")));


            Parameter parameter = new Parameter();
            parameter.setParameterKey("KeyName");
            parameter.setParameterValue(keyChainName);
            List parameters = new ArrayList();
            parameters.add(parameter);
            createRequest.setParameters(parameters);
            System.out.println("Creating a stack called " + createRequest.getStackName() + ".");
            amazonCloudFormationClient.createStack(createRequest);

            // Wait for stack to be created
            // Note that you could use SNS notifications on the CreateStack call to track the progress of the stack creation
            System.out.println("Stack creation completed, the stack " + stackName + " completed with " + waitForCompletion(amazonCloudFormationClient, stackName));

            // Show all the stacks for this account along with the resources for each stack
            for (Stack stack : amazonCloudFormationClient.describeStacks(new DescribeStacksRequest()).getStacks()) {
                System.out.println("Stack : " + stack.getStackName() + " [" + stack.getStackStatus().toString() + "]");

                DescribeStackResourcesRequest stackResourceRequest = new DescribeStackResourcesRequest();
                stackResourceRequest.setStackName(stack.getStackName());
                for (StackResource resource : amazonCloudFormationClient.describeStackResources(stackResourceRequest).getStackResources()) {
                    System.out.format("    %1$-40s %2$-25s %3$s\n", resource.getResourceType(), resource.getLogicalResourceId(), resource.getPhysicalResourceId());
                }
            }

            // Lookup a resource by its logical name
            DescribeStackResourcesRequest logicalNameResourceRequest = new DescribeStackResourcesRequest();
            logicalNameResourceRequest.setStackName(stackName);
            logicalNameResourceRequest.setLogicalResourceId(logicalResourceName);
            System.out.format("Looking up resource name %1$s from stack %2$s\n", logicalNameResourceRequest.getLogicalResourceId(), logicalNameResourceRequest.getStackName());
            for (StackResource resource : amazonCloudFormationClient.describeStackResources(logicalNameResourceRequest).getStackResources()) {
                System.out.format("    %1$-40s %2$-25s %3$s\n", resource.getResourceType(), resource.getLogicalResourceId(), resource.getPhysicalResourceId());
            }

            //deleteStack

        } catch (AmazonServiceException ase) {
            System.out.println("Caught an AmazonServiceException, which means your request made it "
                    + "to AWS CloudFormation, but was rejected with an error response for some reason.");
            System.out.println("Error Message:    " + ase.getMessage());
            System.out.println("HTTP Status Code: " + ase.getStatusCode());
            System.out.println("AWS Error Code:   " + ase.getErrorCode());
            System.out.println("Error Type:       " + ase.getErrorType());
            System.out.println("Request ID:       " + ase.getRequestId());
        } catch (AmazonClientException ace) {
            System.out.println("Caught an AmazonClientException, which means the client encountered "
                    + "a serious internal problem while trying to communicate with AWS CloudFormation, "
                    + "such as not being able to access the network.");
            System.out.println("Error Message: " + ace.getMessage());
        }
    }

    // Convert a stream into a single, newline separated string
    public static String convertStreamToString(InputStream in) throws Exception {

        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        StringBuilder stringbuilder = new StringBuilder();
        String line = null;
        while ((line = reader.readLine()) != null) {
            stringbuilder.append(line + "\n");
        }
        in.close();
        return stringbuilder.toString();
    }

    // Wait for a stack to complete transitioning
    // End stack states are:
    //    CREATE_COMPLETE
    //    CREATE_FAILED
    //    DELETE_FAILED
    //    ROLLBACK_FAILED
    // OR the stack no longer exists
    public static String waitForCompletion(AmazonCloudFormation stackbuilder, String stackName) throws Exception {

        DescribeStacksRequest wait = new DescribeStacksRequest();
        wait.setStackName(stackName);
        Boolean completed = false;
        String stackStatus = "Unknown";
        String stackReason = "";

        System.out.print("Waiting");

        while (!completed) {
            List<Stack> stacks = stackbuilder.describeStacks(wait).getStacks();
            if (stacks.isEmpty()) {
                completed = true;
                stackStatus = "NO_SUCH_STACK";
                stackReason = "Stack has been deleted";
            } else {
                for (Stack stack : stacks) {
                    if (stack.getStackStatus().equals(StackStatus.CREATE_COMPLETE.toString()) ||
                            stack.getStackStatus().equals(StackStatus.CREATE_FAILED.toString()) ||
                            stack.getStackStatus().equals(StackStatus.ROLLBACK_FAILED.toString()) ||
                            stack.getStackStatus().equals(StackStatus.DELETE_FAILED.toString())) {
                        completed = true;
                        stackStatus = stack.getStackStatus();
                        stackReason = stack.getStackStatusReason();
                    }
                }
            }

            // Show we are waiting
            System.out.print(".");

            // Not done yet so sleep for 30 seconds.
            if (!completed) Thread.sleep(30000);
        }

        // Show we are done
        System.out.print("done\n");

        return stackStatus + " (" + stackReason + ")";
    }

    @org.junit.AfterClass
    public static void deleteStack() throws Exception {
        // Delete the stack
            DeleteStackRequest deleteRequest = new DeleteStackRequest();
            deleteRequest.setStackName(stackName);
            System.out.println("Deleting the stack called " + deleteRequest.getStackName() + ".");
            amazonCloudFormationClient.deleteStack(deleteRequest);

            // Wait for stack to be deleted
            // Note that you could used SNS notifications on the original CreateStack call to track the progress of the stack deletion
            System.out.println("Stack creation completed, the stack " + stackName + " completed with " + waitForCompletion(amazonCloudFormationClient, stackName));
    }

    @org.junit.Test
    public void testStackCreated() {
                    // Lookup a resource by its logical name
            DescribeStackResourcesRequest logicalNameResourceRequest = new DescribeStackResourcesRequest();
            logicalNameResourceRequest.setStackName(stackName);
            logicalNameResourceRequest.setLogicalResourceId(logicalResourceName);
            System.out.format("Looking up resource name %1$s from stack %2$s\n", logicalNameResourceRequest.getLogicalResourceId(), logicalNameResourceRequest.getStackName());
            for (StackResource resource : amazonCloudFormationClient.describeStackResources(logicalNameResourceRequest).getStackResources()) {
                System.out.format("    %1$-40s %2$-25s %3$s\n", resource.getResourceType(), resource.getLogicalResourceId(), resource.getPhysicalResourceId());
            }

    }

}
