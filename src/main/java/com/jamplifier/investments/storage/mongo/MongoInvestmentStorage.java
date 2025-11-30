package com.jamplifier.investments.storage.mongo;

import com.jamplifier.investments.InvestmentsPlugin;
import com.jamplifier.investments.investment.Investment;
import com.jamplifier.investments.storage.InvestmentStorage;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import org.bson.Document;
import org.bukkit.configuration.file.FileConfiguration;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * MongoDB implementation of InvestmentStorage.
 * 
 * Document schema:
 * {
 *   _id: "<uuid>",
 *   autoCollect: true/false,
 *   investments: [
 *     { invested: "10000.00", profit: "150.00" },
 *     ...
 *   ]
 * }
 */
public class MongoInvestmentStorage implements InvestmentStorage {

    private final InvestmentsPlugin plugin;

    private MongoClient client;
    private MongoCollection<Document> collection;

    public MongoInvestmentStorage(InvestmentsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() throws Exception {
        FileConfiguration cfg = plugin.getConfig();

        String uri = cfg.getString("mongodb.uri", "mongodb://localhost:27017");
        String dbName = cfg.getString("mongodb.database", "investments");
        String collName = cfg.getString("mongodb.collection", "player_investments");

        // Create client & collection
        client = MongoClients.create(uri);
        MongoDatabase db = client.getDatabase(dbName);
        collection = db.getCollection(collName);

        plugin.getLogger().info("[Investments] Connected to MongoDB: " + uri + " / " + dbName + "." + collName);
    }

    @Override
    public List<Investment> loadInvestments(UUID owner) {
        if (collection == null) return Collections.emptyList();

        Document doc = collection.find(Filters.eq("_id", owner.toString())).first();
        if (doc == null) {
            return Collections.emptyList();
        }

        List<Document> invDocs = doc.getList("investments", Document.class, Collections.emptyList());
        List<Investment> list = new ArrayList<>();

        for (Document invDoc : invDocs) {
            String investedStr = invDoc.getString("invested");
            String profitStr = invDoc.getString("profit");

            if (investedStr == null) {
                continue;
            }

            try {
                BigDecimal invested = new BigDecimal(investedStr);
                BigDecimal profit = profitStr != null ? new BigDecimal(profitStr) : BigDecimal.ZERO;
                list.add(new Investment(owner, invested, profit));
            } catch (NumberFormatException ex) {
                plugin.getLogger().warning("[Investments] Invalid BigDecimal in Mongo for " + owner + ": " + ex.getMessage());
            }
        }

        return list;
    }

    @Override
    public boolean loadAutoCollect(UUID owner) {
        if (collection == null) return false;

        Document doc = collection.find(Filters.eq("_id", owner.toString())).first();
        if (doc == null) return false;

        Boolean value = doc.getBoolean("autoCollect");
        return value != null && value;
    }

    @Override
    public void saveProfile(UUID owner, List<Investment> investments, boolean autoCollect) {
        if (collection == null) return;

        List<Document> invDocs = new ArrayList<>();
        for (Investment inv : investments) {
            Document invDoc = new Document("invested", inv.getInvested().toPlainString())
                    .append("profit", inv.getProfit().toPlainString());
            invDocs.add(invDoc);
        }

        Document doc = new Document("_id", owner.toString())
                .append("autoCollect", autoCollect)
                .append("investments", invDocs);

        collection.replaceOne(
                Filters.eq("_id", owner.toString()),
                doc,
                new ReplaceOptions().upsert(true)
        );
    }

    @Override
    public void close() {
        if (client != null) {
            try {
                client.close();
            } catch (Exception ignored) {
            }
        }
    }
}
