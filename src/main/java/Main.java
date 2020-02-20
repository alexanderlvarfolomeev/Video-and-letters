import javax.imageio.ImageIO;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;

import java.util.List;

import com.xuggle.mediatool.IMediaReader;
import com.xuggle.mediatool.MediaListenerAdapter;
import com.xuggle.mediatool.ToolFactory;
import com.xuggle.mediatool.event.IVideoPictureEvent;
import com.xuggle.xuggler.Global;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;


/**
 * Using {@link IMediaReader}, takes a media container, finds the first video stream, decodes that
 * stream, and then writes video frames out to a PNG image file every 5
 * seconds, based on the video presentation timestamps.
 *
 * @author aclarke
 * @author trebor
 */

public class Main extends MediaListenerAdapter {

    private List<BufferedImage> list;

    static final Set<Character> CHARS = Set.of('U', 'T', 'A', 'W', 'Y', 'R', 'B', 'O', 'G', 'M', 'I', 'V', 'X', 'D');

    static final Map<Character, Character> SHIFT = Map.of(
            '\'', 'V', ')', 'B', 'N', 'W', 'o', 'O', '0', 'O',
            '4', 'W', '9', 'O', 'y', 'A', '\\', 'W', '|', 'I');

    private static final int STEP = 24;

    public static final double SECONDS_BETWEEN_FRAMES = 1.0 / STEP;

    /**
     * The number of micro-seconds between frames.
     */

    public static final long MICRO_SECONDS_BETWEEN_FRAMES =
            (long) (Global.DEFAULT_PTS_PER_SECOND * SECONDS_BETWEEN_FRAMES);

    /**
     * Time of last frame write.
     */

    private static long mLastPtsWrite = Global.NO_PTS;

    /**
     * The video stream index, used to ensure we display frames from one
     * and only one video stream from the media container.
     */

    private int mVideoStreamIndex = -1;

    public static void main(String[] args) {
        new Main("src/main/resources/task.mp4");
    }

    /**
     * Construct a DecodeAndCaptureFrames which reads and captures
     * frames from a video file.
     *
     * @param filename the name of the media file to read
     */

    public Main(String filename) {
        list = new LinkedList<>();

        IMediaReader reader = ToolFactory.makeReader(filename);
        reader.setBufferedImageTypeToGenerate(BufferedImage.TYPE_3BYTE_BGR);
        reader.addListener(this);
        ITesseract tesseract = new Tesseract();
        tesseract.setDatapath("src/main/resources/tessdata");
        StringBuilder buffer = new StringBuilder();
        BufferedImage prev = null;
        char c;
        int j = 0;
        int step = 0;

        while (reader.readPacket() == null || !list.isEmpty()) {
            while (!list.isEmpty()) {
                if (!imagesIsEqual(list.get(0), prev)) {
                    try {
                        String str = tesseract.doOCR(list.get(0));
                        c = str.charAt(0);
                        if (!CHARS.contains(c)) {
                            c = SHIFT.get(c);
                        }
                        if (c == 'Y' && imageIsBlack(list.get(0))) {
                            c = 'V';
                        }
                        if (c == 'V' && imageIsW(list.get(0))) {
                            c = 'W';
                        }
                        buffer.append(c);
                        //ImageIO.write(list.get(0), "png", new File("test/frame" + (j++) + '_' + c + ".png"));
                    } catch (TesseractException e) {
                        //do nothing
                    } catch (IOException e) {
                        //
                    }
                }
                System.out.println(step++);
                prev = list.remove(0);
            }
        }
        System.out.println(LetterCounter.count(buffer.toString()));
    }

    /**
     * Called after a video frame has been decoded from a media stream.
     * Optionally a BufferedImage version of the frame may be passed
     * if the calling {@link IMediaReader} instance was configured to
     * create BufferedImages.
     * <p>
     * This method blocks, so return quickly.
     */

    public void onVideoPicture(IVideoPictureEvent event) {
        try {
            if (event.getStreamIndex() != mVideoStreamIndex) {

                if (-1 == mVideoStreamIndex) {
                    mVideoStreamIndex = event.getStreamIndex();
                } else return;
            }

            if (mLastPtsWrite == Global.NO_PTS)
                mLastPtsWrite = event.getTimeStamp() - MICRO_SECONDS_BETWEEN_FRAMES;

            if (event.getTimeStamp() - mLastPtsWrite >= MICRO_SECONDS_BETWEEN_FRAMES) {

                double seconds = ((double) event.getTimeStamp())
                        / Global.DEFAULT_PTS_PER_SECOND;

                list.add(event.getImage());
                mLastPtsWrite += MICRO_SECONDS_BETWEEN_FRAMES;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        while (list.size() > 1000) {
            System.out.print('?');
        }
    }

    static boolean imagesIsEqual(BufferedImage image1, BufferedImage image2) {
        if (image2 == null) return true;
        int diff = 0;
        for (int x = 0; x < image1.getWidth(); x++) {
            for (int y = 0; y < image1.getHeight(); y++) {
                int rgb1 = image1.getRGB(x, y);
                int rgb2 = image2.getRGB(x, y);
                int res = Math.max(Math.max(Math.abs((rgb1 & 0xff) - (rgb2 & 0xff)), Math.abs(((rgb1 >> 8) & 0xff) - ((rgb2 >> 8) & 0xff))),
                        Math.max(Math.abs(((rgb1 >> 16) & 0xff) - ((rgb2 >> 16) & 0xff)), Math.abs(((rgb1 >> 24) & 0xff) - ((rgb2 >> 24) & 0xff))));
                if (res >= 15)
                    diff++;
            }
        }
        return diff * 60 < 256 * 256;
    }

    static boolean imageIsBlack(BufferedImage image) {
        int diff = 0;
        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                int rgb = image.getRGB(x, y);
                int res = Math.max(Math.max((rgb & 0xff), ((rgb >> 8) & 0xff)), ((rgb >> 16) & 0xff));
                if (res < 100) diff++;
            }
        }
        return diff > 0.7 * 256 * 256;
    }

    static boolean imageIsW(BufferedImage image) throws IOException {
        BufferedImage imageW = ImageIO.read(new File("src/main/resources/frameW.png"));
        return imagesIsEqual(image, imageW);
    }
}