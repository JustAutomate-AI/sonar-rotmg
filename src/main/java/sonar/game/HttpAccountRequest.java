package sonar.game;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * The one request Sonar makes to the game's own servers.
 * <p>
 * Everything else Sonar knows it learned by reading traffic that was going past
 * anyway, which no server can see. This is different in kind: an authenticated
 * call, carrying the account's access token, made by a program that is not the
 * game client, from the address a session is live on. So there is exactly one of
 * them left and it is made at most once a day - see {@code GameData.updateExalts},
 * which owns that decision.
 * <p>
 * What went: {@code char/list}, the whole character list. It was fetched in the
 * Pet Yard and the Daily Quest Room, and for a while speculatively on every login
 * as well, where the server was expected to refuse it. The Characters page now
 * builds itself from the characters it watches being played, and a character that
 * has gone is marked by hand rather than deduced from the list no longer carrying
 * it.
 */
public final class HttpAccountRequest {

    private HttpAccountRequest() {
    }

    /**
     * Requests the account's exaltation progress for every class.
     *
     * @param accessToken Access token of the currently logged in user.
     * @return Exalt data of all characters, or null if the server refused.
     */
    public static String getPowerUpStats(String accessToken) throws IOException {
        return webRequest(accessToken, "account/listPowerUpStats");
    }

    private static String webRequest(String accessToken, String requestType) throws IOException {
        String encoded = URLEncoder.encode(accessToken, "UTF-8");
        String query =
            "do_login=true&accessToken=" +
            encoded +
            "&game_net=Unity&play_platform=Unity&game_net_user_id";

        URL url = new URL("https://www.realmofthemadgod.com/" + requestType + "?" + query);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
        con.setDoOutput(true);
        con.setRequestMethod("POST");
        // a server that never answers must not hold the thread open for the rest of the session
        con.setConnectTimeout(10000);
        con.setReadTimeout(20000);

        try (OutputStream os = con.getOutputStream()) {
            byte[] input = query.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        boolean ok = con.getResponseCode() == HttpURLConnection.HTTP_OK;
        InputStream body = ok ? con.getInputStream() : con.getErrorStream();
        if (body == null) return null;
        StringBuilder response = new StringBuilder();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) response.append(line);
        }
        if (ok) return response.toString();
        System.out.println("Exalt stats refused: " + response);
        return null;
    }
}
