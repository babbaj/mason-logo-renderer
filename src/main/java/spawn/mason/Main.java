package spawn.mason;


import com.mongodb.client.*;
import org.bson.Document;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class Main {

    private static final int SZ = 144;

    public static void main(String[] args) throws IOException {
        MongoClient mongoClient = MongoClients.create("mongodb://babbaj:babbaj1@ds023448.mlab.com:23448/heroku_7scmkkrf");
        //MongoDatabase database = mongoClient.getDatabase("masonweb");
        MongoDatabase database = mongoClient.getDatabase("heroku_7scmkkrf");
        MongoCollection<Document> collection = database.getCollection("regions");
        FindIterable<Document> documents = collection.find();

        final BufferedImage image = renderImage(documents);
        ImageIO.write(image, "png", new File("output.png"));
    }

    static class IntPair {
        final int x;
        final int y;
        IntPair(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    private static BufferedImage renderImage(FindIterable<Document> iterable) {
        List<Document> documents =  StreamSupport.stream(iterable.spliterator(), false)
            .collect(Collectors.toList());
        final int maxX = maxDoc(documents.stream(), true, false).get();
        final int minX = maxDoc(documents.stream(), true, true).get();
        final int maxY = maxDoc(documents.stream(), false, false).get();
        final int minY = maxDoc(documents.stream(), false, true).get();
        final int width = maxX - minX;
        final int height = maxY - minY;

        // approximately 11k x 11k
        final BufferedImage image = new BufferedImage((width + 1) * SZ, (height + 1) * SZ, BufferedImage.TYPE_BYTE_BINARY);
        fillWhite(image);

        documents.forEach((Consumer<Document>)doc -> {
            final boolean[][] obsidian = parseObsidian(doc.getString("regionData"));
            final IntPair pos = parseId(doc.getString("sectionId"));
            final int baseX = (pos.x + Math.abs(minX)) * SZ;
            final int baseY = (pos.y + Math.abs(minY)) * SZ;


            for (int i = 0; i < SZ; i++)
            for (int j = 0; j < SZ; j++) {
                final boolean b = obsidian[i][j];
                image.setRGB(baseX + i, baseY + j, b ? 0 : 0xFF_FF_FF_FF);
             }
        });

        return image;
    }

    private static void fillWhite(BufferedImage image) {
        Graphics2D graphics = image.createGraphics();

        graphics.setPaint(new Color ( 255, 255, 255 ));
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
    }

    private static Optional<Integer> maxDoc(Stream<Document> documents, boolean x, boolean reversed) {
        return maxInt(documents.map(doc -> parseId(doc.getString("sectionId"))), x, reversed);
    }

    // pretty dumb api lmao
    private static Optional<Integer> maxInt(Stream<IntPair> stream, boolean x, boolean reversed) {
        final ToIntFunction<IntPair> getter = x ? p -> p.x : p -> p.y;
        Comparator<IntPair> comp = Comparator.comparingInt(getter);
        if (reversed) comp = comp.reversed();
        return stream.max(comp).map(getter::applyAsInt);
    }


    private static IntPair parseId(String id) {
        String[] split = id.split(",");
        return new IntPair(Integer.parseInt(split[0]), Integer.parseInt(split[1]));
    }

    private static boolean[][] parseObsidian(String data) {
        final String[] lines = data.split("\\n");
        final boolean[][] obsidian = new boolean[SZ][SZ];
        for (int z = 0; z < SZ; z++) {
            for (int x = 0; x < SZ; x++) {
                final char c = lines[z].charAt(x);
                if (c == '1') {
                    obsidian[x][z] = true;
                } else if (c != '0') {
                    throw new IllegalStateException("trolled");
                }
            }
        }
        return obsidian;
    }

}
