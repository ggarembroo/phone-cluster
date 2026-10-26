package ai.phonecluster.stand;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
  private static final int BG = 0xFF070A0F;
  private static final int PANEL = 0xFF101721;
  private static final int PANEL_2 = 0xFF151E2A;
  private static final int LINE = 0xFF263345;
  private static final int TEXT = 0xFFF4F7F2;
  private static final int MUTED = 0xFF8C99A9;
  private static final int ACCENT = 0xFFB8FF3D;
  private static final int CYAN = 0xFF55D7FF;
  private static final int DANGER = 0xFFFF6572;

  private static final int REQ_MODEL = 41;
  private static final int NODE_PORT = 50053;
  private static final int SERVER_PORT = 8080;

  private static final int SCREEN_HOME = 0;
  private static final int SCREEN_HOST = 1;
  private static final int SCREEN_RUN = 2;
  private static final int SCREEN_CHAT = 3;

  private int screen = SCREEN_HOME;
  private String modelPath;
  private String modelName;
  private String endpointDraft = "";
  private String hostEndpoint = "";
  private ProcessController nodeProcess;
  private ProcessController modelProcess;
  private WebView webView;
  private TextView nodeStatus;
  private TextView endpointView;
  private TextView modelView;
  private TextView chatBench;
  private EditText endpointInput;
  private TextView chatStatus;
  private final ExecutorService io = Executors.newSingleThreadExecutor();

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Window window = getWindow();
    window.setStatusBarColor(BG);
    window.setNavigationBarColor(BG);
    window.getDecorView().setSystemUiVisibility(0);
    showHome();
  }

  @Override
  public void onBackPressed() {
    if (screen == SCREEN_CHAT) {
      stopModel();
      showRun();
      return;
    }
    if (screen == SCREEN_HOST || screen == SCREEN_RUN) {
      if (screen == SCREEN_HOST) stopNode();
      showHome();
      return;
    }
    super.onBackPressed();
  }

  @Override
  protected void onDestroy() {
    stopNode();
    stopModel();
    if (webView != null) {
      webView.destroy();
      webView = null;
    }
    io.shutdownNow();
    super.onDestroy();
  }

  private void showHome() {
    screen = SCREEN_HOME;
    LinearLayout page = page();
    addBrand(page);
    addSpace(page, 26);
    addHero(page);
    addSpace(page, 22);
    addRoleCard(
        page,
        "01 / HOST NODE",
        "Turn this phone into compute. Standard ggml-rpc server. No model required.",
        "HOST A NODE",
        false,
        this::showHost);
    addSpace(page, 12);
    addRoleCard(
        page,
        "02 / RUN MODEL",
        "Pick a GGUF model, pair a node, and open the local chat. The model stays on this phone.",
        "PAIR AND RUN",
        true,
        this::showRun);
    addSpace(page, 24);
    addPairingNote(page);
    addFooter(page);
    setPage(page);
  }

  private void showHost() {
    screen = SCREEN_HOST;
    LinearLayout page = page();
    addBack(page, this::showHome);
    addKicker(page, "ROLE 01 / COMPUTE NODE", ACCENT);
    addTitle(page, "HOST A NODE", 38);
    addBody(page, "Expose this phone on the local network. The other phone will send tensor work here.");
    addSpace(page, 22);

    LinearLayout status = card(PANEL, 22, LINE, 1);
    nodeStatus = text("NODE OFFLINE", 12, MUTED, Typeface.MONOSPACE);
    nodeStatus.setLetterSpacing(0.1f);
    status.addView(nodeStatus);
    addSpace(status, 14);
    endpointView = text(hostEndpoint.isEmpty() ? "start the node to get a pair address" : hostEndpoint, 20, TEXT, Typeface.MONOSPACE);
    endpointView.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
    status.addView(endpointView);
    addSpace(status, 16);
    LinearLayout statusActions = row();
    statusActions.addView(action("START NODE", true, this::startNode), weight());
    statusActions.addView(spaceView(8));
    statusActions.addView(action("COPY", false, this::copyEndpoint), weight());
    statusActions.addView(spaceView(8));
    statusActions.addView(action("STOP", false, this::stopNode), weight());
    status.addView(statusActions);
    page.addView(status);
    addSpace(page, 16);
    addWarning(page, "Trusted networks only. The reference ggml-rpc server has no authentication.");
    addSpace(page, 18);
    addLogCard(page);
    addSpace(page, 18);
    addBody(page, "Keep both phones on the same Wi-Fi, or use USB tethering. The endpoint must be reachable from the model phone.");
    addFooter(page);
    setPage(page);
    if (nodeProcess != null && nodeProcess.isRunning()) {
      nodeStatus.setText("NODE LISTENING");
      nodeStatus.setTextColor(ACCENT);
      endpointView.setText(hostEndpoint);
    }
  }

  private void showRun() {
    screen = SCREEN_RUN;
    LinearLayout page = page();
    addBack(page, this::showHome);
    addKicker(page, "ROLE 02 / MODEL PHONE", CYAN);
    addTitle(page, "RUN A MODEL", 38);
    addBody(page, "Choose a local GGUF model and paste the pair address from the host phone.");
    addSpace(page, 22);

    LinearLayout pair = card(PANEL, 22, LINE, 1);
    pair.addView(label("PAIR ADDRESS", 11, MUTED));
    addSpace(pair, 8);
    endpointInput = new EditText(this);
    endpointInput.setText(endpointDraft);
    endpointInput.setHint("192.168.1.42:50053");
    endpointInput.setHintTextColor(0xFF586779);
    endpointInput.setTextColor(TEXT);
    endpointInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
    endpointInput.setTypeface(Typeface.MONOSPACE);
    endpointInput.setSingleLine(true);
    endpointInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
    endpointInput.setBackground(rounded(PANEL_2, 14, LINE, 1));
    endpointInput.setPadding(dp(14), dp(12), dp(14), dp(12));
    pair.addView(endpointInput, matchWidth());
    addSpace(pair, 12);
    pair.addView(action("PASTE FROM CLIPBOARD", false, this::pasteEndpoint), matchWidth());
    page.addView(pair);
    addSpace(page, 14);

    LinearLayout model = card(PANEL, 22, LINE, 1);
    model.addView(label("LOCAL MODEL", 11, MUTED));
    addSpace(model, 8);
    modelView = text(modelName == null ? "no GGUF selected" : modelName, 15, modelName == null ? MUTED : TEXT, Typeface.MONOSPACE);
    modelView.setMaxLines(2);
    model.addView(modelView);
    addSpace(model, 14);
    model.addView(action("CHOOSE GGUF", false, this::chooseModel), matchWidth());
    page.addView(model);
    addSpace(page, 18);

    page.addView(action("START SESSION", true, this::startSession), matchWidth());
    addSpace(page, 10);
    addBody(page, "Model files are copied into the app's private storage. Nothing is uploaded.");
    addSpace(page, 18);
    addLogCard(page);
    addFooter(page);
    setPage(page);
  }

  private void showChat() {
    screen = SCREEN_CHAT;
    FrameLayout root = new FrameLayout(this);
    root.setBackgroundColor(BG);
    LinearLayout column = new LinearLayout(this);
    column.setOrientation(LinearLayout.VERTICAL);
    column.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    root.addView(column);

    LinearLayout top = new LinearLayout(this);
    top.setGravity(Gravity.CENTER_VERTICAL);
    top.setPadding(dp(16), dp(10), dp(16), dp(10));
    top.setBackgroundColor(BG);
    TextView live = text("● LIVE", 11, ACCENT, Typeface.MONOSPACE);
    live.setLetterSpacing(0.12f);
    top.addView(live);
    chatStatus = text("  local session", 11, MUTED, Typeface.MONOSPACE);
    top.addView(chatStatus);
    top.addView(spaceView(0, 1, 1));
    chatBench = text("", 10, ACCENT, Typeface.MONOSPACE);
    chatBench.setPadding(0, 0, dp(8), 0);
    top.addView(chatBench);
    top.addView(action("BENCH", false, this::runBench));
    top.addView(spaceView(8));
    top.addView(action("END", false, this::endSession));
    column.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));

    View line = new View(this);
    line.setBackgroundColor(LINE);
    column.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

    webView = new WebView(this);
    WebSettings settings = webView.getSettings();
    settings.setJavaScriptEnabled(true);
    settings.setDomStorageEnabled(true);
    settings.setAllowFileAccess(false);
    settings.setMediaPlaybackRequiresUserGesture(true);
    webView.setWebViewClient(new WebViewClient());
    webView.setBackgroundColor(BG);
    column.addView(webView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
    webView.loadUrl("http://127.0.0.1:" + SERVER_PORT);

    setContentView(root);
  }

  private void startNode() {
    if (nodeProcess != null && nodeProcess.isRunning()) {
      toast("Node is already running");
      return;
    }
    String ip = localIpv4();
    if (ip == null) {
      toast("No local IPv4 address. Connect Wi-Fi or USB tethering.");
      return;
    }
    hostEndpoint = ip + ":" + NODE_PORT;
    showHost();
    nodeStatus.setText("STARTING...");
    nodeStatus.setTextColor(CYAN);
    String binary = nativeBinary("libpc_ggml_rpc_server.so");
    ProcessController p = new ProcessController(binary, "node", new ProcessController.Listener() {
      @Override
      public void onLine(String line) {
        appendLog(line);
      }

      @Override
      public void onExit(int code) {
        runOnUiThread(() -> {
          if (screen == SCREEN_HOST && nodeStatus != null) {
            nodeStatus.setText("NODE OFFLINE");
            nodeStatus.setTextColor(MUTED);
          }
        });
      }
    });
    boolean ok = p.start(
        "-H", "0.0.0.0",
        "-p", String.valueOf(NODE_PORT),
        "-t", String.valueOf(threads()));
    if (!ok) {
      toast("Could not start the node");
      return;
    }
    nodeProcess = p;
    nodeStatus.setText("NODE LISTENING");
    nodeStatus.setTextColor(ACCENT);
    endpointView.setText(hostEndpoint);
    appendLog("pair address " + hostEndpoint);
  }

  private void stopNode() {
    if (nodeProcess != null) {
      nodeProcess.stop();
      nodeProcess = null;
    }
    if (screen == SCREEN_HOST && nodeStatus != null) {
      nodeStatus.setText("NODE OFFLINE");
      nodeStatus.setTextColor(MUTED);
    }
  }

  private void startSession() {
    if (modelProcess != null && modelProcess.isRunning()) {
      toast("Session is already running");
      return;
    }
    if (modelPath == null || !new File(modelPath).isFile()) {
      toast("Choose a GGUF model first");
      return;
    }
    String endpoint = endpointInput == null ? "" : endpointInput.getText().toString().trim();
    endpoint = endpoint.replace("http://", "").replace("https://", "").replace("/", "");
    if (endpoint.isEmpty() || !endpoint.contains(":")) {
      toast("Enter the pair address as host:port");
      return;
    }
    endpointDraft = endpoint;
    String binary = nativeBinary("libpc_llama_server.so");
    ProcessController p = new ProcessController(binary, "model", new ProcessController.Listener() {
      @Override
      public void onLine(String line) {
        appendLog(line);
      }

      @Override
      public void onExit(int code) {
        runOnUiThread(() -> {
          if (screen == SCREEN_CHAT && chatStatus != null) {
            chatStatus.setText("  session ended");
            chatStatus.setTextColor(DANGER);
          }
        });
      }
    });
    boolean ok = p.start(
        "-m", modelPath,
        "--rpc", endpoint,
        "--host", "127.0.0.1",
        "--port", String.valueOf(SERVER_PORT),
        "-ngl", "99",
        "-c", "2048",
        "-t", String.valueOf(threads()));
    if (!ok) {
      toast("Could not start llama-server");
      return;
    }
    modelProcess = p;
    toast("Starting model on " + endpoint);
    io.execute(() -> {
      boolean ready = waitForPort("127.0.0.1", SERVER_PORT, 90000);
      runOnUiThread(() -> {
        if (ready && modelProcess != null && modelProcess.isRunning()) {
          showChat();
        } else {
          toast("Model did not become ready. Check the log.");
          if (modelProcess != null) modelProcess.stop();
          modelProcess = null;
          if (screen == SCREEN_RUN) showRun();
        }
      });
    });
  }

  private void stopModel() {
    if (modelProcess != null) {
      modelProcess.stop();
      modelProcess = null;
    }
    if (webView != null) {
      webView.destroy();
      webView = null;
    }
  }

  private void endSession() {
    stopModel();
    showRun();
  }

  private void chooseModel() {
    if (endpointInput != null) endpointDraft = endpointInput.getText().toString().trim();
    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
    intent.addCategory(Intent.CATEGORY_OPENABLE);
    intent.setType("*/*");
    startActivityForResult(intent, REQ_MODEL);
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode != REQ_MODEL || resultCode != RESULT_OK || data == null || data.getData() == null) {
      return;
    }
    Uri uri = data.getData();
    String name = displayName(uri);
    if (name == null || !name.toLowerCase(Locale.US).endsWith(".gguf")) {
      toast("Choose a .gguf file");
      return;
    }
    toast("Copying model...");
    io.execute(() -> {
      try {
        File dir = new File(getFilesDir(), "models");
        if (!dir.exists()) dir.mkdirs();
        File out = new File(dir, "phone-cluster.gguf");
        try (InputStream in = getContentResolver().openInputStream(uri);
             OutputStream os = new FileOutputStream(out)) {
          if (in == null) throw new IllegalStateException("Could not open the selected file");
          byte[] buffer = new byte[1024 * 1024];
          int n;
          while ((n = in.read(buffer)) > 0) os.write(buffer, 0, n);
        }
        modelPath = out.getAbsolutePath();
        modelName = name;
        runOnUiThread(() -> {
          toast("Model ready");
          if (screen == SCREEN_RUN) showRun();
        });
      } catch (Exception e) {
        runOnUiThread(() -> toast("Model copy failed: " + e.getMessage()));
      }
    });
  }

  private void copyEndpoint() {
    if (hostEndpoint == null || hostEndpoint.isEmpty()) {
      toast("Start the node first");
      return;
    }
    ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
    if (cm == null) return;
    cm.setPrimaryClip(ClipData.newPlainText("Phone Cluster pair address", hostEndpoint));
    toast("Pair address copied");
  }

  private void pasteEndpoint() {
    ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
    if (cm == null || !cm.hasPrimaryClip() || cm.getPrimaryClip() == null) {
      toast("Clipboard is empty");
      return;
    }
    ClipData clip = cm.getPrimaryClip();
    if (clip.getItemCount() == 0) return;
    CharSequence value = clip.getItemAt(0).coerceToText(this);
    if (endpointInput != null) {
      endpointInput.setText(String.valueOf(value).trim());
      endpointInput.setSelection(endpointInput.getText().length());
    }
  }

  private void runBench() {
    if (chatBench != null) chatBench.setText("running");
    io.execute(() -> {
      String result = "benchmark failed";
      try {
        HttpURLConnection c = (HttpURLConnection) new URL("http://127.0.0.1:" + SERVER_PORT + "/completion").openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(5000);
        c.setReadTimeout(180000);
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        String body = "{\"prompt\":\"The capital of France is\",\"n_predict\":32,\"temperature\":0,\"stream\":false}";
        try (OutputStream os = c.getOutputStream()) {
          os.write(body.getBytes(StandardCharsets.UTF_8));
        }
        int code = c.getResponseCode();
        if (code >= 200 && code < 300) {
          StringBuilder sb = new StringBuilder();
          try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
          }
          JSONObject json = new JSONObject(sb.toString());
          JSONObject timings = json.optJSONObject("timings");
          double tps = timings == null ? 0 : timings.optDouble("predicted_per_second", 0);
          if (tps <= 0 && timings != null) {
            double n = timings.optDouble("predicted_n", 0);
            double ms = timings.optDouble("predicted_ms", 0);
            if (n > 0 && ms > 0) tps = n / (ms / 1000.0);
          }
          result = String.format(Locale.US, "%.2f tok/s", tps);
        } else {
          result = "HTTP " + code;
        }
      } catch (Exception e) {
        result = "benchmark failed";
      }
      final String value = result;
      runOnUiThread(() -> {
        if (chatBench != null) chatBench.setText(value);
        toast(value);
      });
    });
  }

  private void showHomeFromBack() {
    showHome();
  }

  private String displayName(Uri uri) {
    try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
      if (cursor != null && cursor.moveToFirst()) {
        int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
        if (index >= 0) return cursor.getString(index);
      }
    } catch (Exception ignored) {
    }
    return null;
  }

  private String nativeBinary(String name) {
    return new File(getApplicationInfo().nativeLibraryDir, name).getAbsolutePath();
  }

  private int threads() {
    int cores = Runtime.getRuntime().availableProcessors();
    return Math.max(2, Math.min(4, cores));
  }

  private String localIpv4() {
    String best = null;
    int bestScore = -1;
    try {
      for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
        if (!ni.isUp() || ni.isLoopback()) continue;
        String name = ni.getName() == null ? "" : ni.getName().toLowerCase(Locale.US);
        for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
          InetAddress address = ia.getAddress();
          if (!(address instanceof Inet4Address) || address.isLoopbackAddress() || address.isLinkLocalAddress()) continue;
          String ip = address.getHostAddress();
          if (ip == null) continue;
          int score = 0;
          if (ip.startsWith("192.168.42.")) score += 10;
          if (name.contains("wlan") || name.contains("wifi")) score += 5;
          if (name.contains("rndis") || name.contains("usb")) score += 6;
          if (name.contains("eth")) score += 3;
          if (score > bestScore) {
            bestScore = score;
            best = ip;
          }
        }
      }
    } catch (Exception ignored) {
    }
    return best;
  }

  private boolean waitForPort(String host, int port, long timeoutMs) {
    long end = System.currentTimeMillis() + timeoutMs;
    while (System.currentTimeMillis() < end) {
      if (modelProcess == null || !modelProcess.isRunning()) return false;
      try (Socket socket = new Socket()) {
        socket.connect(new java.net.InetSocketAddress(host, port), 400);
        return true;
      } catch (Exception ignored) {
        try {
          Thread.sleep(500);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return false;
        }
      }
    }
    return false;
  }

  private void appendLog(String line) {
    runOnUiThread(() -> {
      View current = findViewById(android.R.id.content);
      TextView log = current == null ? null : current.findViewWithTag("log");
      if (log == null) return;
      String old = log.getText().toString();
      String next = (old + line + "\n");
      if (next.length() > 12000) next = next.substring(next.length() - 12000);
      log.setText(next);
    });
  }

  private void addBrand(LinearLayout page) {
    LinearLayout mark = row();
    mark.setGravity(Gravity.CENTER_VERTICAL);
    TextView dot = text("●", 13, ACCENT, Typeface.DEFAULT);
    mark.addView(dot);
    TextView kicker = text("  OPEN MOBILE AI STAND / 0.1", 10, ACCENT, Typeface.MONOSPACE);
    kicker.setLetterSpacing(0.14f);
    mark.addView(kicker);
    page.addView(mark);
  }

  private void addHero(LinearLayout page) {
    LinearLayout hero = card(PANEL, 28, LINE, 1);
    TextView label = text("SPLIT INFERENCE", 10, CYAN, Typeface.MONOSPACE);
    label.setLetterSpacing(0.16f);
    hero.addView(label);
    addSpace(hero, 14);
    TextView title = text("ONE MODEL.\nTWO PHONES.", 38, TEXT, Typeface.DEFAULT_BOLD);
    title.setLineSpacing(dp(2), 0.9f);
    hero.addView(title);
    addSpace(hero, 12);
    hero.addView(body("Run a local LLM across two Android phones with standard ggml-rpc. No cloud. No account. No PC."));
    addSpace(hero, 22);
    LinearLayout diagram = row();
    diagram.setGravity(Gravity.CENTER_VERTICAL);
    TextView a = chip("PHONE A", CYAN);
    TextView bridge = text("  ↔  ", 22, ACCENT, Typeface.MONOSPACE);
    TextView b = chip("PHONE B", ACCENT);
    diagram.addView(a);
    diagram.addView(bridge);
    diagram.addView(b);
    hero.addView(diagram);
    addSpace(hero, 14);
    TextView foot = text("llama.cpp  ·  ggml-rpc  ·  local network", 11, MUTED, Typeface.MONOSPACE);
    hero.addView(foot);
    page.addView(hero);
  }

  private void addRoleCard(LinearLayout page, String label, String description, String button, boolean primary, Runnable action) {
    LinearLayout card = card(PANEL, 22, LINE, 1);
    TextView l = text(label, 10, primary ? CYAN : ACCENT, Typeface.MONOSPACE);
    l.setLetterSpacing(0.14f);
    card.addView(l);
    addSpace(card, 10);
    card.addView(body(description));
    addSpace(card, 16);
    card.addView(action(button, primary, action), matchWidth());
    page.addView(card);
  }

  private void addPairingNote(LinearLayout page) {
    LinearLayout note = card(PANEL_2, 20, 0xFF334258, 1);
    note.addView(label("HOW PAIRING WORKS", 10, MUTED));
    addSpace(note, 10);
    note.addView(body("01  Host opens a node and copies the address.\n02  Model phone pastes it and starts the session.\n03  Chat opens locally. Nothing leaves the room."));
    page.addView(note);
  }

  private void addFooter(LinearLayout page) {
    addSpace(page, 24);
    TextView f = text("STANDARD GGML-RPC  /  TRUSTED LAN ONLY  /  OPEN STAND", 9, 0xFF586779, Typeface.MONOSPACE);
    f.setLetterSpacing(0.08f);
    f.setGravity(Gravity.CENTER);
    page.addView(f);
  }

  private void addLogCard(LinearLayout page) {
    LinearLayout logCard = card(0xFF0B1119, 18, LINE, 1);
    logCard.addView(label("SESSION LOG", 10, MUTED));
    addSpace(logCard, 8);
    TextView log = text("waiting for a role...", 11, 0xFF9AA9B7, Typeface.MONOSPACE);
    log.setTag("log");
    log.setMaxLines(14);
    log.setVerticalScrollBarEnabled(true);
    log.setMovementMethod(new android.text.method.ScrollingMovementMethod());
    logCard.addView(log);
    page.addView(logCard);
  }

  private void addBack(LinearLayout page, Runnable action) {
    TextView back = text("‹  BACK", 12, MUTED, Typeface.MONOSPACE);
    back.setPadding(0, dp(4), 0, dp(18));
    back.setOnClickListener(v -> action.run());
    page.addView(back);
  }

  private void addKicker(LinearLayout page, String text, int color) {
    TextView t = text(text, 10, color, Typeface.MONOSPACE);
    t.setLetterSpacing(0.12f);
    page.addView(t);
  }

  private void addTitle(LinearLayout page, String title, int size) {
    addSpace(page, 10);
    TextView t = text(title, size, TEXT, Typeface.DEFAULT_BOLD);
    t.setLetterSpacing(-0.03f);
    page.addView(t);
    addSpace(page, 10);
  }

  private void addBody(LinearLayout page, String text) {
    page.addView(body(text));
  }

  private TextView body(String text) {
    TextView t = text(text, 14, MUTED, Typeface.DEFAULT);
    t.setLineSpacing(dp(4), 1.0f);
    return t;
  }

  private void addWarning(LinearLayout page, String text) {
    LinearLayout warning = card(0xFF201A15, 16, 0xFF5D4321, 1);
    TextView t = text("!", 12, 0xFFFFB86B, Typeface.MONOSPACE);
    warning.addView(t);
    addSpace(warning, 6);
    warning.addView(body(text));
    page.addView(warning);
  }

  private LinearLayout page() {
    ScrollView scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    scroll.setBackgroundColor(BG);
    scroll.setClipToPadding(false);
    LinearLayout page = new LinearLayout(this);
    page.setOrientation(LinearLayout.VERTICAL);
    page.setPadding(dp(22), dp(20), dp(22), dp(36));
    scroll.addView(page);
    page.setTag(scroll);
    return page;
  }

  private void setPage(LinearLayout page) {
    ScrollView scroll = (ScrollView) page.getTag();
    setContentView(scroll);
  }

  private LinearLayout card(int color, int radius, int stroke, int strokeWidth) {
    LinearLayout card = new LinearLayout(this);
    card.setOrientation(LinearLayout.VERTICAL);
    card.setPadding(dp(18), dp(18), dp(18), dp(18));
    card.setBackground(rounded(color, radius, stroke, strokeWidth));
    return card;
  }

  private GradientDrawable rounded(int color, int radius, int stroke, int strokeWidth) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(dp(radius));
    if (strokeWidth > 0) d.setStroke(dp(strokeWidth), stroke);
    return d;
  }

  private TextView chip(String label, int color) {
    TextView v = text(label, 10, color, Typeface.MONOSPACE);
    v.setGravity(Gravity.CENTER);
    v.setPadding(dp(10), dp(8), dp(10), dp(8));
    v.setBackground(rounded(0xFF0B1119, 10, color, 1));
    return v;
  }

  private TextView action(String label, boolean primary, Runnable action) {
    TextView v = text(label, 12, primary ? BG : TEXT, Typeface.DEFAULT_BOLD);
    v.setGravity(Gravity.CENTER);
    v.setPadding(dp(12), dp(13), dp(12), dp(13));
    v.setBackground(primary
        ? rounded(ACCENT, 14, ACCENT, 1)
        : rounded(PANEL_2, 14, LINE, 1));
    v.setClickable(true);
    v.setFocusable(true);
    v.setOnClickListener(x -> action.run());
    return v;
  }

  private TextView label(String text, int size, int color) {
    TextView t = text(text, size, color, Typeface.MONOSPACE);
    t.setLetterSpacing(0.1f);
    return t;
  }

  private TextView text(String text, int size, int color, Typeface typeface) {
    TextView t = new TextView(this);
    t.setText(text);
    t.setTextColor(color);
    t.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
    t.setTypeface(typeface);
    return t;
  }

  private LinearLayout row() {
    LinearLayout row = new LinearLayout(this);
    row.setOrientation(LinearLayout.HORIZONTAL);
    return row;
  }

  private Space spaceView(int widthDp) {
    return spaceView(widthDp, 0, 0);
  }

  private Space spaceView(int widthDp, int heightDp, int weight) {
    Space s = new Space(this);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
        widthDp > 0 ? dp(widthDp) : 0,
        heightDp > 0 ? dp(heightDp) : 0,
        weight);
    s.setLayoutParams(lp);
    return s;
  }

  private LinearLayout.LayoutParams weight() {
    return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
  }

  private LinearLayout.LayoutParams matchWidth() {
    return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
  }

  private void addSpace(LinearLayout parent, int dp) {
    Space s = new Space(this);
    s.setLayoutParams(new LinearLayout.LayoutParams(1, dp(dp)));
    parent.addView(s);
  }

  private int dp(int value) {
    return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
  }

  private void toast(String text) {
    Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
  }

  private static final class ProcessController {
    interface Listener {
      void onLine(String line);
      void onExit(int code);
    }

    private final String binary;
    private final String tag;
    private final Listener listener;
    private Process process;
    private Thread reader;
    private Thread waiter;

    ProcessController(String binary, String tag, Listener listener) {
      this.binary = binary;
      this.tag = tag;
      this.listener = listener;
    }

    boolean start(String... args) {
      try {
        List<String> command = new ArrayList<>();
        command.add(binary);
        Collections.addAll(command, args);
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        pb.environment().put("HOME", System.getProperty("user.home", "/data/local/tmp"));
        process = pb.start();
        reader = new Thread(() -> {
          try (BufferedReader r = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) listener.onLine(line);
          } catch (Exception ignored) {
          }
        }, tag + "-reader");
        reader.start();
        waiter = new Thread(() -> {
          int code = -1;
          try {
            code = process.waitFor();
          } catch (InterruptedException ignored) {
          }
          listener.onExit(code);
        }, tag + "-waiter");
        waiter.start();
        return true;
      } catch (Exception e) {
        listener.onLine("launch failed: " + e.getMessage());
        return false;
      }
    }

    boolean isRunning() {
      return process != null && process.isAlive();
    }

    void stop() {
      Process p = process;
      process = null;
      if (p != null) {
        p.destroy();
        try {
          p.waitFor();
        } catch (InterruptedException ignored) {
        }
        if (p.isAlive()) p.destroyForcibly();
      }
    }
  }
}
