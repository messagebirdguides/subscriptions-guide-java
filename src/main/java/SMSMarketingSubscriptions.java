import com.messagebird.MessageBirdClient;
import com.messagebird.MessageBirdService;
import com.messagebird.MessageBirdServiceImpl;
import com.mongodb.client.*;
import io.github.cdimascio.dotenv.Dotenv;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.json.JSONObject;
import spark.ModelAndView;
import spark.template.mustache.MustacheTemplateEngine;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Updates.combine;
import static com.mongodb.client.model.Updates.set;
import static spark.Spark.get;
import static spark.Spark.post;

public class SMSMarketingSubscriptions {
    public static void main(String[] args) {
        Dotenv dotenv = Dotenv.load();

        // Create a MessageBirdService
        final MessageBirdService messageBirdService = new MessageBirdServiceImpl(dotenv.get("MESSAGEBIRD_API_KEY"));
        // Add the service to the client
        final MessageBirdClient messageBirdClient = new MessageBirdClient(messageBirdService);

        MongoClient mongoClient = MongoClients.create("mongodb://localhost:27017");
        MongoDatabase database = mongoClient.getDatabase("myproject");

        // Handle incoming webhooks
        post("/webhook",
                (req, res) ->
                {
                    JSONObject requestPayload = new JSONObject(req.body());

                    // Read request
                    String number = (String) requestPayload.get("originator");
                    String text = (String) requestPayload.get("body");
                    text = text.toLowerCase();

                    // Find subscriber in our database
                    MongoCollection<Document> subscribers = database.getCollection("subscribers");
                    Document doc = subscribers.find(eq("number", number)).first();

                    BigInteger phoneNumber = new BigInteger(number);
                    final List<BigInteger> phones = new ArrayList<BigInteger>();
                    phones.add(phoneNumber);

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

                    if (doc != null && (Boolean) doc.get("subscribed") == false && text.equals("subscribe")) {
                        // The user has sent the "subscribe" keyword
                        // and was already found in the database in an
                        // unsubscribed state. We resubscribe them by
                        // updating their database entry.
                        Bson query = combine(set("subscribed", true));
                        subscribers.updateOne(eq("number", number), query);

                        // Notify the user
                        messageBirdClient.sendMessage(dotenv.get("MESSAGEBIRD_ORIGINATOR"), "Thanks for re-subscribing to our list! Send STOP anytime if you no longer want to receive messages from us.", phones);
                    }

                    if (doc != null && (Boolean) doc.get("subscribed") == true && text.equals("stop")) {
                        // The user has sent the "stop" keyword, indicating
                        // that they want to unsubscribe from messages.
                        // They were found in the database, so we mark
                        // them as unsubscribed and update the entry.
                        Bson query = combine(set("subscribed", false));
                        subscribers.updateOne(eq("number", number), query);

                        // Notify the user
                        messageBirdClient.sendMessage(dotenv.get("MESSAGEBIRD_ORIGINATOR"), "Sorry to see you go! You will not receive further marketing messages from us.", phones);
                    }

                    // Return any response, MessageBird won't parse this
                    res.status(200);

                    return "";
                }
        );

        get("/",
                (req, res) ->
                {
                    Map<String, Object> model = new HashMap<>();

                    // Get number of subscribers to show on the form
                    MongoCollection<Document> subscribers = database.getCollection("subscribers");

                    Long count = subscribers.countDocuments(eq("subscribed", true));
                    model.put("count", count);

                    return new ModelAndView(model, "home.mustache");
                },

                new MustacheTemplateEngine()
        );

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
    }
}