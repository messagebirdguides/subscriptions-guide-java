# Setting SMS Marketing Subscriptions with MessageBird

### ⏱ 30 min build time

## Why build SMS marketing subscriptions?

In this MessageBird Developer Tutorial, you’ll learn how to implement an SMS marketing campaign subscription application powered by the [MessageBird SMS Messaging API](https://developers.messagebird.com/docs/sms-messaging) and enable your subscribers to seamlessly opt-in and out.

SMS makes it incredibly easy for businesses to reach consumers everywhere at any time, directly on their mobile devices. For many people, these messages are a great way to discover things like discounts and special offers from a company, while others might find them annoying. For this reason, it’s important and also required by law in many countries to provide clear opt-in and opt-out mechanisms for SMS broadcast lists. To make these work independently of a website it's useful to assign a programmable [virtual mobile number](https://www.messagebird.com/en/numbers) to your SMS campaign and handle incoming messages programmatically so users can control their subscription with basic command keywords.

We'll walk you through the following steps:

* A person can send the keyword _SUBSCRIBE_ to a specific VMN that the company includes in their advertising material; the opt-in is immediately confirmed.
* If the person no longer wants to receive messages they can send the keyword _STOP_ to the same number; the opt-out is also confirmed.
* An administrator can enter a message in a form on a website. Then they can send this message to all confirmed subscribers immediately.

## Getting Started

Since our sample application is built in Java, you need to have [[Java 1.8](https://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html) and [Maven](https://maven.apache.org/) installed.

We've provided the source code of the sample application in the [MessageBird Developer Tutorials GitHub repository](https://github.com/messagebirdguides/subscriptions-guide-java), which you can either clone with git or from where you can download a ZIP file with the source code to your computer.

After saving the code, import the project into your IDE. Maven should automatically download all the dependencies necessary.

The sample application uses [MongoDB](https://mongodb.github.io/mongo-java-driver/) to provide an in-memory database for testing, so you don't need to configure an external database.

## Prerequisites for Receiving Messages

### Overview

This tutorial describes receiving messages using MessageBird. From a high-level viewpoint, receiving is relatively simple: an application defines a _webhook_ URL, which you assign to a number purchased in the MessageBird Dashboard using a flow. A [webhook](https://en.wikipedia.org/wiki/Webhook) is a URL on your site that doesn't render a page to users but is like an API endpoint that can be triggered by other servers. Every time someone sends a message to that number, MessageBird collects it and forwards it to the webhook URL where you can process it.

### Exposing your Development Server with ngrok

When working with webhooks, an external service like MessageBird needs to access your application, so the URL must be public. During development, though, you're typically working in a local development environment that is not publicly available. There are various tools and services available that allow you to quickly expose your development environment to the Internet by providing a tunnel from a public URL to your local machine. One of the most popular tools is [ngrok](https://ngrok.com/).

You can [download ngrok here for free](https://ngrok.com/download) as a single-file binary for almost every operating system, or optionally sign up for an account to access additional features.

You can start a tunnel by providing a local port number on which your application runs. We will run our Ruby server on port 4567, so you can launch your tunnel with this command:

```
ngrok http 4567
```

After you've launched the tunnel, ngrok displays your temporary public URL along with some other information. We'll need that URL in a minute.

Another common tool for tunneling your local machine is [localtunnel.me](https://localtunnel.me/), which you can have a look at if you're facing problems with ngrok. It works in virtually the same way but requires you to install [NPM](https://www.npmjs.com/) first.

### Getting an Inbound Number

A requirement for receiving messages is a dedicated inbound number. Virtual mobile numbers look and work in a similar way to regular mobile numbers, however, instead of being attached to a mobile device via a SIM card, they live in the cloud and can process incoming SMS and voice calls. MessageBird offers numbers from different countries for a low monthly fee; [feel free to explore our low-cost programmable and configurable numbers](https://www.messagebird.com/en/numbers).

Purchasing a number is quite easy:

1. Go to the '[Numbers](https://dashboard.messagebird.com/en/numbers)' section in the left-hand side of your Dashboard and click the blue button '[Buy a number](https://dashboard.messagebird.com/en/vmn/buy-number)' in the top-right side of your screen.
2. Pick the country in which you and your customers are located, and make sure both the SMS capability is selected.
3. Choose one number from the selection and the duration for which you want to pay now.
4. Confirm by clicking 'Buy Number' in the bottom-right of your screen.
![Buy a number](https://developers.messagebird.com/assets/images/screenshots/subscription-node/buy-a-number.png)

Awesome, you’ve set up your first virtual mobile number! 🎉

**Pro-Tip**: Check out our Help Center for more information about [virtual mobile numbers])https://support.messagebird.com/hc/en-us/sections/201958489-Virtual-Numbers and [country restrictions](https://support.messagebird.com/hc/en-us/sections/360000108538-Country-info-Restrictions).

### Connect Number to the Webhook

So you have a number now, but MessageBird has no idea what to do with it. That's why now you need to define a _Flow_ that links your number to your webhook. This is how you do it:

#### STEP ONE
On the ‘[Numbers](https://dashboard.messagebird.com/en/numbers)’ section of the MessageBird Dashboard, click the ‘Add a new flow’ icon next to the number you purchased.

![Add a new flow](https://developers.messagebird.com/assets/images/screenshots/subscription-node/add-new-flow.png)

#### STEP TWO
Hit `‘Create Custom Flow’` and give your flow a name, choose ‘SMS’ as the trigger and hit ‘Next’.

![Setup New Flow](https://developers.messagebird.com/assets/images/screenshots/subscription-node/setup-new-flow.png)

#### STEP THREE
The number is already attached to the first step ‘SMS’. Add a new step by pressing the small `+`, choose `Forward to URL` and select `POST` as the method; copy the output from the ngrok command in the URL and add `/webhook` to it—this is the name of the route we use to handle incoming messages in our sample application. Click on ‘Save’ when ready.

![Forward to URL](https://developers.messagebird.com/assets/images/screenshots/subscription-node/forward-to-URL.png)
#### STEP FOUR
Ready! Hit ‘Publish’ on the right top of the screen to activate your flow. Well done, another step closer to testing incoming messages!

![SMS Subscriptions](https://developers.messagebird.com/assets/images/screenshots/subscription-node/SMS-Subscriptions.png)

**Pro-Tip**: You can edit the name of the flow by clicking on the icon next to button ‘Back to Overview’ and pressing ‘Rename flow’.

![Rename flow](https://developers.messagebird.com/assets/images/screenshots/subscription-node/rename-flow.png)

## Configuring the MessageBird SDK

While the MessageBird SDK and an API key are not required to receive messages, it is necessary for sending confirmations and marketing messages. The SDK is defined in `pom.xml` and loaded with these statements:

``` java
import com.messagebird.MessageBirdClient;
import com.messagebird.MessageBirdService;
import com.messagebird.MessageBirdServiceImpl;

import io.github.cdimascio.dotenv.Dotenv;

Dotenv dotenv = Dotenv.load();

// Create a MessageBirdService
final MessageBirdService messageBirdService = new MessageBirdServiceImpl(dotenv.get("MESSAGEBIRD_API_KEY"));

// Add the service to the client
final MessageBirdClient messageBirdClient = new MessageBirdClient(messageBirdService);
```

You need to provide a MessageBird API key, as well as the phone number you registered so that you can use it as the originator via environment variables. Thanks to [dotenv](https://mvnrepository.com/artifact/io.github.cdimascio/java-dotenv) you can also supply these through an `.env` file stored in your resources:

```
MESSAGEBIRD_API_KEY=YOUR-API-KEY
MESSAGEBIRD_ORIGINATOR=+31970XXXXXXX
```

The [API access (REST) tab](https://dashboard.messagebird.com/en/developers/access) in the [Developers section](https://dashboard.messagebird.com/en/developers/settings) of the MessageBird Dashboard allows you to create or retrieve a live API key.

## Receiving Messages

Now we're fully prepared for receiving inbound messages; let's have a look at the actual implementation of our `/webhook` route:

```java
// Handle incoming webhooks
post("/webhook",
    (req, res) ->
    {
        JSONObject requestPayload = new JSONObject(req.body());

        // Read request
        String number = (String) requestPayload.get("originator");
        String text = (String) requestPayload.get("body");
        text = text.toLowerCase();
```

The webhook receives some request parameters from MessageBird; however, we're only interested in two of them: the  `originator ` (the number of the user who sent the message) and the `body` (the text of the message). The content is trimmed and converted into lower case so we can easily do case-insensitive command detection.

```java
// Find subscriber in our database
MongoCollection<Document> subscribers = database.getCollection("subscribers");
Document doc = subscribers.find(eq("number", number)).first();
```

Using our MongoDB client, we'll look up the number in a collection aptly named subscribers.

We're looking at three potential cases:

* The user has sent _SUBSCRIBE_ and the number doesn’t exist. In that case, the subscriber should be added and opted in.
* The user has submitted _SUBSCRIBE_ and the number exists but has opted out. In that case, it should be opted in (again).
* The user has sent _STOP_ and the number exists and has opted in. In that case, it should be opted out.

For each of those cases, a differently worded confirmation message should be sent. All incoming messages that don't fit any of these cases are ignored and don't get a reply. You can optimize this behavior by sending a help message with all supported commands.

The implementation of each case is similar, so let's only look at one of them here:

```java
if (doc == null && text.equals("subscribe")) {
    // The user has sent the "subscribe" keyword
    // and is not stored in the database yet, so
    // we add them to the database.
    Document entry = new Document("number", number)
            .append("subscribed", true);
    subscribers.insertOne(entry);

    // Notify the user
    messageBirdClient.sendMessage(dotenv.get("MESSAGEBIRD_ORIGINATOR"), "Thanks for subscribing to our list! Send STOP anytime if you no longer want to receive messages from us.", phones);
}
```

If no `doc` (database entry) exists and the text matches “subscribe”, the script executes an insert query that stores a document with the number and the boolean variable `subscribed` set to `true`. The user is notified by calling the `messageBirdClient.sendMessage` SDK method and, as parameters, passing the originator from our configuration, a recipient list with the number from the incoming message and a hardcoded text body.

## Sending Messages

### Showing Form

We've defined a simple form with a single text area and a submit button, and stored it as a Mustache template in `views/home.mustache`. It is rendered for a GET request on the root of the application. As a small hint for the admin, we're also showing the number of subscribers in the database.

### Processing input

The form submits its content as a POST request to the `/send` route. The implementation of this route fetches all subscribers that have opted in from the database and then uses the MessageBird SDK to send a message to them. It is possible to send a message to up to 50 receivers in a single API call, so the script splits a list of subscribers that is longer than 50 numbers (highly unlikely during testing, unless you have amassed an impressive collection of phones) into blocks of 50 numbers each. Sending uses the `messageBirdClient.sendMessage` SDK method which you've already seen in the previous section.

Here's the full code block:

```java
post("/send",
    (req, res) ->
    {
        String message = req.queryParams("message");

        Map<String, Object> model = new HashMap<>();

        // Get number of subscribers to show on the form
        MongoCollection<Document> subscribers = database.getCollection("subscribers");

        FindIterable<Document> iterable = subscribers.find(eq("subscribed", true));
        MongoCursor<Document> cursor = iterable.iterator();

        final List<BigInteger> recipients = new ArrayList<BigInteger>();
        Integer count = 0;

        // Collect all numbers
        while (cursor.hasNext()) {
            Document d = cursor.next();
            BigInteger phoneNumber = new BigInteger((String) d.get("number"));
            recipients.add(phoneNumber);
            count += 1;
            if (count == subscribers.countDocuments() || count % 50 == 0) {
                // We have reached either the end of our list or 50 numbers,
                // which is the maximum that MessageBird accepts in a single
                // API call, so we send the message and then, if any numbers
                // are remaining, start a new list
                messageBirdClient.sendMessage(dotenv.get("MESSAGEBIRD_ORIGINATOR"), message, recipients);
                recipients.clear();
            }
        }

        model.put("count", count);
        return new ModelAndView(model, "sent.mustache");
    },

    new MustacheTemplateEngine()
);
```

## Testing the Application

Double-check that you’ve set up your number correctly with a flow that forwards incoming messages to a ngrok URL and that the tunnel is still running. Keep in mind that whenever you start a fresh tunnel, you'll get a new URL, so you have to update it in the flows accordingly.

Build and run the application through your IDE.

While keeping the console open, take out your phone, launch the SMS app and send a message to your virtual mobile number with the keyword “subscribe”. A few seconds later, a confirmation message should arrive shortly. Open http://localhost:4567/ in your browser (or your tunnel URL), and you should also see that there's one subscriber. Try sending yourself a message now. And voilá, your marketing system is ready!

You can adapt the sample application for production by adding some authorization to the web form; otherwise, anybody could send messages to your subscribers. Don't forget to download the code from the [MessageBird Developer Tutorials GitHub repository](https://github.com/messagebirdguides/subscriptions-guide-java).

**Nice work!** 🎉

You've just built your own marketing system with MessageBird!

## Start building!

Want to build something similar but not quite sure how to get started? Feel free to let us know at support@messagebird.com; we'd love to help!
