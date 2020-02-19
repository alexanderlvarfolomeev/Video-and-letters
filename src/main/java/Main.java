import javax.imageio.ImageIO;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import java.awt.*;
import java.util.List;

import com.xuggle.mediatool.IMediaReader;
import com.xuggle.mediatool.MediaListenerAdapter;
import com.xuggle.mediatool.ToolFactory;
import com.xuggle.mediatool.event.IVideoPictureEvent;
import com.xuggle.xuggler.Global;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.Tesseract1;
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

    public static final double SECONDS_BETWEEN_FRAMES = 1.0/STEP;

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
        Main main = new Main("src/main/resources/task.mp4");
        ITesseract tesseract = new Tesseract();
        tesseract.setDatapath("src/main/resources/tessdata");
        StringBuilder buffer = new StringBuilder();
        char c;
        int j = 0;
        for (int i = 1; i < main.list.size(); ++i) {
            if (!imagesIsEqual(main.list.get(i), main.list.get(i - 1))) {
                try {
                    c = tesseract.doOCR(main.list.get(i)).charAt(0);
                    if (!CHARS.contains(c)) {
                        c = SHIFT.get(c);
                    }
                    buffer.append(c);
                    ImageIO.write(main.list.get(i), "png", new File("test/" + (j++) + ".png"));
                } catch (TesseractException e) {
                    //do nothing
                } catch (IOException e) {
                    //
                }
            }
        }
        System.out.println(buffer.toString());
        Path file;
    }

    /**
     * Construct a DecodeAndCaptureFrames which reads and captures
     * frames from a video file.
     *
     * @param filename the name of the media file to read
     */

    public Main(String filename) {
        list = new ArrayList<>();

        IMediaReader reader = ToolFactory.makeReader(filename);
        reader.setBufferedImageTypeToGenerate(BufferedImage.TYPE_3BYTE_BGR);
        reader.addListener(this);

        while (reader.readPacket() == null) {}
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
    }

    static boolean imagesIsEqual(BufferedImage image1, BufferedImage image2) {
        if (image1.getWidth() == image2.getWidth() && image1.getHeight() == image2.getHeight()) {
            for (int x = 0; x < image1.getWidth(); x++) {
                for (int y = 0; y < image1.getHeight(); y++) {
                    if (image1.getRGB(x, y) != image2.getRGB(x, y))
                        return false;
                }
            }
        } else {
            return false;
        }
        return true;
    }
}