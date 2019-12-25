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
import java.util.concurrent.TimeUnit;
import java.util.function.ToIntFunction;
import java.util.stream.*;

public class Main {

    private static final int SZ = 144;

    public static void main(String[] args) throws IOException {
        final long now = System.currentTimeMillis();

        MongoClient mongoClient = MongoClients.create("mongodb://babbaj:babbaj1@ds023448.mlab.com:23448/heroku_7scmkkrf");
        //MongoDatabase database = mongoClient.getDatabase("masonweb");
        MongoDatabase database = mongoClient.getDatabase("heroku_7scmkkrf");
        MongoCollection<Document> collection = database.getCollection("regions");
        FindIterable<Document> iterable = collection.find();

        final List<Section> sections =  StreamSupport.stream(iterable.spliterator(), false)
            .map(Section::fromDocument)
            .collect(Collectors.toList());

        final long start = startTime(sections);
        final long end = endTime(sections);
        final long step = TimeUnit.HOURS.toMillis(3);
        final int count = (int)Math.ceil(((end - start) / (double)step)) + 1;

        final File outDir = new File("output");
        outDir.mkdir();
        for (File f : outDir.listFiles()) { f.delete(); }


        IntStream.iterate(0, i -> i + 1).parallel()
            .limit(count)
            .forEach(i -> {
                final long t = start + i * step;
                try {
                    final BufferedImage image = renderImage(sections, t);
                    drawTimestamp(image.getGraphics(), t);
                    final String name = String.format("%03d", i);
                    final File f = new File("output/" + name + ".png");
                    System.out.println(i);
                    ImageIO.write(image, "png", f);
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            });


        System.out.println((System.currentTimeMillis() - now) / 1000 + "s");
    }

    private static void drawTimestamp(Graphics g, long time) {
        Font currentFont = g.getFont();
        Font newFont = currentFont.deriveFont(currentFont.getSize() * 10F);
        g.setFont(newFont);
        g.setColor(Color.BLACK);
        g.drawString(String.valueOf(time), 100, 200);
    }

    static class IntPair {
        final int x;
        final int z;
        IntPair(int x, int z) {
            this.x = x;
            this.z = z;
        }
    }

    public static class Section extends IntPair {
        final long startTime;
        final long endTime;
        final boolean[][] obsidian;

        public Section(int x, int z, long startTime, long endTime, boolean[][] obsidian) {
            super(x, z);
            this.startTime = startTime;
            this.endTime = endTime;
            this.obsidian = obsidian;
        }

        public static Section fromDocument(Document doc) {
            final boolean[][] obsidian = parseObsidian(doc.getString("regionData"));
            final int x = doc.getInteger("X");
            final int z = doc.getInteger("Z");
            final List<Document> history = doc.getList("history", Document.class);
            // TODO: figure out fastest possible time
            final long start = getTimestamps(history).min().getAsLong();
            final long end = getTimestamps(history).max().getAsLong();

            return new Section(x, z, start, end, obsidian);
        }

        private static LongStream getTimestamps(List<Document> history) {
            return history.stream()
                .filter(d -> d.getString("status").equals("PLACING") || d.getString("status").equals("DONE"))
                .mapToLong(d -> d.get("timestamp", Date.class).getTime());
        }
    }


    private static BufferedImage renderImage(List<Section> sections, long time) {
        final int maxX = maxInt(sections.stream(), true, false).get();
        final int minX = maxInt(sections.stream(), true, true).get();
        final int maxZ = maxInt(sections.stream(), false, false).get();
        final int minZ = maxInt(sections.stream(), false, true).get();
        final int width = maxX - minX;
        final int height = maxZ - minZ;

        // approximately 11k x 11k
        final BufferedImage image = new BufferedImage((width + 1) * SZ, (height + 1) * SZ, BufferedImage.TYPE_BYTE_BINARY);
        fillWhite(image);

        sections.forEach(sec -> {
            final int baseX = (sec.x + Math.abs(minX)) * SZ;
            final int baseZ = (sec.z + Math.abs(minZ)) * SZ;

            renderSection(image, sec, baseX, baseZ, time);
        });

        return image;
    }

    private static void renderSection(BufferedImage image, Section sec, int x, int z, long time) {
        if (time < sec.startTime) return;

        final boolean[][] obsidian = sec.obsidian;
        for (int i = 0; i < SZ; i++)
            for (int j = 0; j < SZ; j++) {
                final boolean b = obsidian[i][j];
                image.setRGB(x + i, z + j, b ? 0 : 0xFF_FF_FF_FF);
            }
    }

    private static void fillWhite(BufferedImage image) {
        Graphics2D graphics = image.createGraphics();

        graphics.setPaint(new Color (255, 255, 255));
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
    }

    private static long startTime(List<Section> sections) {
        return sections.stream()
            .mapToLong(s -> s.startTime)
            .min()
            .getAsLong();
    }

    private static long endTime(List<Section> sections) {
        return sections.stream()
            .mapToLong(s -> s.endTime)
            .max()
            .getAsLong();
    }


    // pretty dumb api lmao
    private static Optional<Integer> maxInt(Stream<? extends IntPair> stream, boolean x, boolean reversed) {
        final ToIntFunction<IntPair> getter = x ? p -> p.x : p -> p.z;
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
