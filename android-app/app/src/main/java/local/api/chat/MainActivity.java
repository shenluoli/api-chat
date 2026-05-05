package local.api.chat;

import android.app.Activity;
import android.app.AlertDialog;
import android.animation.LayoutTransition;
import android.animation.ObjectAnimator;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.Base64;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private static final String PREFS = "phone_api_chat";
    private static final Pattern MARKDOWN_IMAGE = Pattern.compile("!\\[([^\\]]*)\\]\\(([^\\s)]+)(?:\\s+\"[^\"]*\")?\\)");
    private static final Pattern BARE_IMAGE = Pattern.compile("(?i)\\b((?:https?://|file://)[^\\s)]+\\.(?:png|jpe?g|webp|gif)(?:\\?[^\\s)]*)?|data:image/[a-z0-9.+-]+;base64,[a-z0-9+/=\\r\\n]+)");
    private static final ProviderPreset[] PROVIDERS = {
            new ProviderPreset("deepseek", "DeepSeek", "https://api.deepseek.com/v1",
                    new String[]{"deepseek-v4-flash", "deepseek-v4-pro", "deepseek-chat", "deepseek-reasoner"}),
            new ProviderPreset("openai", "OpenAI", "https://api.openai.com/v1",
                    new String[]{"gpt-4.1", "gpt-4.1-mini", "gpt-4o", "gpt-4o-mini"},
                    new String[]{"gpt-image-1", "gpt-image-1-mini", "dall-e-3"}),
            new ProviderPreset("anthropic", "Claude", "https://api.anthropic.com/v1",
                    new String[]{"claude-opus-4-1-20250805", "claude-sonnet-4-20250514", "claude-3-7-sonnet-latest", "claude-3-5-haiku-latest"}),
            new ProviderPreset("gemini", "Google Gemini", "https://generativelanguage.googleapis.com/v1beta/openai",
                    new String[]{"gemini-2.5-pro", "gemini-2.5-flash", "gemini-2.0-flash"}),
            new ProviderPreset("qwen", "通义千问", "https://dashscope.aliyuncs.com/compatible-mode/v1",
                    new String[]{"qwen-plus", "qwen-max", "qwen-turbo", "qwen-long"}),
            new ProviderPreset("moonshot", "Moonshot Kimi", "https://api.moonshot.cn/v1",
                    new String[]{"kimi-k2-0711-preview", "moonshot-v1-8k", "moonshot-v1-32k", "moonshot-v1-128k"}),
            new ProviderPreset("zhipu", "智谱 GLM", "https://open.bigmodel.cn/api/paas/v4",
                    new String[]{"glm-4-plus", "glm-4-air", "glm-4-flash"}),
            new ProviderPreset("openrouter", "OpenRouter", "https://openrouter.ai/api/v1",
                    new String[]{"openai/gpt-4o-mini", "anthropic/claude-3.5-sonnet", "google/gemini-2.5-flash", "deepseek/deepseek-chat"}),
            new ProviderPreset("siliconflow", "硅基流动", "https://api.siliconflow.cn/v1",
                    new String[]{"deepseek-ai/DeepSeek-V3", "deepseek-ai/DeepSeek-R1", "Qwen/Qwen2.5-72B-Instruct"}),
            new ProviderPreset("proxy", "自定义 / 中转", "https://你的中转地址/v1",
                    new String[]{"gpt-4o-mini", "deepseek-chat", "claude-3.5-sonnet", "gemini-2.5-flash"},
                    new String[]{"gpt-image-1", "gpt-image-1-mini", "gpt-image-1.5", "dall-e-3"})
    };

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ExecutorService imageExecutor = Executors.newFixedThreadPool(2);
    private final Map<String, Bitmap> imageCache = new HashMap<>();
    private final List<Conversation> conversations = new ArrayList<>();
    private final Set<String> generatingConversationIds = new LinkedHashSet<>();

    private SharedPreferences prefs;
    private Settings settings;
    private String activeConversationId;
    private LinearLayout messageList;
    private ScrollView scrollView;
    private EditText promptInput;
    private TextView titleView;
    private Button imageModeButton;
    private TextView sendButton;
    private Button modelButton;
    private Button thinkingButton;
    private boolean imageModeEnabled = false;
    private boolean activeGeneration = false;
    private boolean autoFollowBottom = true;
    private boolean touchingMessages = false;
    private boolean messageDragDuringGeneration = false;
    private float messageTouchStartY = 0f;

    private final int bg = Color.rgb(255, 254, 249);
    private final int page = Color.rgb(255, 253, 248);
    private final int text = Color.rgb(35, 35, 33);
    private final int muted = Color.rgb(115, 114, 108);
    private final int line = Color.rgb(229, 224, 216);
    private final int accent = Color.rgb(188, 90, 53);
    private final int accentDark = Color.rgb(136, 68, 48);
    private final int accentSoft = Color.rgb(142, 83, 66);
    private final int sage = Color.rgb(235, 243, 236);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        settings = Settings.load(prefs);
        loadConversations();
        if (activeConversationId == null) createConversation(false);
        buildUi();
        renderMessages();
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        imageExecutor.shutdownNow();
        super.onDestroy();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(12), dp(10), dp(12), dp(10));
        top.setBackgroundColor(bg);

        Button historyButton = iconButton("☰");
        historyButton.setContentDescription("历史对话");
        historyButton.setOnClickListener(v -> showHistoryDialog());
        top.addView(historyButton, new LinearLayout.LayoutParams(dp(38), dp(38)));

        LinearLayout titleBlock = new LinearLayout(this);
        titleBlock.setOrientation(LinearLayout.VERTICAL);
        titleBlock.setPadding(dp(12), 0, dp(10), 0);
        TextView eyebrow = new TextView(this);
        eyebrow.setText("Phone API Chat");
        eyebrow.setTextColor(accent);
        eyebrow.setTextSize(11.5f);
        eyebrow.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        titleView = new TextView(this);
        titleView.setTextColor(text);
        titleView.setTextSize(18);
        titleView.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
        titleView.setSingleLine(true);
        titleBlock.addView(eyebrow);
        titleBlock.addView(titleView);
        top.addView(titleBlock, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        modelButton = iconButton(modelLabel(settings.model));
        modelButton.setTextSize(11.5f);
        modelButton.setSingleLine(true);
        modelButton.setEllipsize(TextUtils.TruncateAt.END);
        modelButton.setContentDescription("切换模型");
        modelButton.setOnClickListener(v -> showModelDialog());
        top.addView(modelButton, new LinearLayout.LayoutParams(dp(82), dp(38)));

        thinkingButton = iconButton(thinkingLabel());
        thinkingButton.setTextSize(11.5f);
        thinkingButton.setContentDescription("\u601d\u8003\u5f00\u5173");
        thinkingButton.setOnClickListener(v -> toggleThinking());
        LinearLayout.LayoutParams thinkingLp = new LinearLayout.LayoutParams(dp(56), dp(38));
        thinkingLp.leftMargin = dp(8);
        top.addView(thinkingButton, thinkingLp);

        Button settingsButton = iconButton("⚙");
        settingsButton.setContentDescription("设置");
        settingsButton.setOnClickListener(v -> showSettingsDialogSmart());
        LinearLayout.LayoutParams settingsLp = new LinearLayout.LayoutParams(dp(38), dp(38));
        settingsLp.leftMargin = dp(8);
        top.addView(settingsButton, settingsLp);
        root.addView(top);

        View divider = new View(this);
        divider.setBackgroundColor(line);
        root.addView(divider, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));

        scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(bg);
        scrollView.setOnTouchListener((target, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                touchingMessages = true;
                messageTouchStartY = event.getY();
                messageDragDuringGeneration = false;
                if (activeGeneration && !isAtBottom()) autoFollowBottom = false;
            } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                touchingMessages = true;
                if (activeGeneration && Math.abs(event.getY() - messageTouchStartY) > dp(3)) {
                    messageDragDuringGeneration = true;
                    autoFollowBottom = false;
                }
            } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                if (activeGeneration && !messageDragDuringGeneration) autoFollowBottom = isAtBottom();
                touchingMessages = false;
            }
            return false;
        });
        scrollView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            if (!activeGeneration) return;
            if (touchingMessages && scrollY != oldScrollY) {
                messageDragDuringGeneration = true;
                autoFollowBottom = false;
                return;
            }
            if (isAtBottom()) {
                autoFollowBottom = true;
            }
        });
        messageList = new LinearLayout(this);
        messageList.setOrientation(LinearLayout.VERTICAL);
        messageList.setPadding(dp(16), dp(28), dp(16), dp(20));
        scrollView.addView(messageList);
        root.addView(scrollView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout composer = new LinearLayout(this);
        composer.setOrientation(LinearLayout.HORIZONTAL);
        composer.setGravity(Gravity.BOTTOM);
        composer.setBaselineAligned(false);
        composer.setClipChildren(false);
        composer.setClipToPadding(false);
        composer.setPadding(dp(14), dp(12), dp(14), dp(14));
        composer.setBackgroundColor(bg);
        promptInput = new EditText(this);
        promptInput.setHint("输入消息...");
        promptInput.setTextColor(text);
        promptInput.setHintTextColor(muted);
        promptInput.setTextSize(15.5f);
        promptInput.setMinLines(1);
        promptInput.setMaxLines(8);
        promptInput.setMinHeight(dp(46));
        promptInput.setMaxHeight(dp(184));
        promptInput.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        promptInput.setVerticalScrollBarEnabled(true);
        promptInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        promptInput.setBackground(makeStrokeBg(Color.rgb(255, 255, 255), line, dp(24)));
        promptInput.setPadding(dp(16), dp(8), dp(16), dp(8));
        promptInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                adjustPromptHeight();
            }
        });
        imageModeButton = iconButton("图");
        imageModeButton.setTextSize(15);
        imageModeButton.setContentDescription("切换文生图");
        imageModeButton.setOnClickListener(v -> toggleImageMode());
        LinearLayout.LayoutParams imageModeLp = new LinearLayout.LayoutParams(dp(44), dp(44));
        imageModeLp.gravity = Gravity.BOTTOM;
        imageModeLp.rightMargin = dp(8);
        composer.addView(imageModeButton, imageModeLp);
        updateImageModeButton();

        LinearLayout.LayoutParams promptLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        promptLp.gravity = Gravity.BOTTOM;
        composer.addView(promptInput, promptLp);

        sendButton = new TextView(this);
        sendButton.setText("↑");
        sendButton.setTextSize(22);
        sendButton.setTextColor(Color.WHITE);
        sendButton.setGravity(Gravity.CENTER);
        sendButton.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        sendButton.setIncludeFontPadding(true);
        sendButton.setMinWidth(0);
        sendButton.setMinHeight(0);
        sendButton.setPadding(0, 0, 0, dp(1));
        sendButton.setBackground(makeSolidBg(accent, dp(23)));
        sendButton.setOnClickListener(v -> submit());
        addPressAnimation(sendButton);
        LinearLayout.LayoutParams sendLp = new LinearLayout.LayoutParams(dp(46), dp(46));
        sendLp.gravity = Gravity.BOTTOM;
        sendLp.leftMargin = dp(10);
        composer.addView(sendButton, sendLp);
        root.addView(composer);

        setContentView(root);
    }

    private void renderMessages() {
        renderMessages(true);
    }

    private void renderMessages(boolean keepAtBottom) {
        Conversation conversation = activeConversation();
        titleView.setText(conversation.title);
        messageList.removeAllViews();

        if (conversation.messages.isEmpty()) {
            TextView emptyTitle = new TextView(this);
            emptyTitle.setText("手机独立使用");
            emptyTitle.setTextColor(text);
            emptyTitle.setTextSize(30);
            emptyTitle.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
            emptyTitle.setLetterSpacing(0f);
            messageList.addView(emptyTitle);

            TextView emptyText = paragraph("点右上角设置填 API Key。顶部可以新建对话，左上角可以切换历史。");
            emptyText.setTextColor(muted);
            emptyText.setTextSize(16);
            LinearLayout.LayoutParams emptyTextLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            emptyTextLp.topMargin = dp(12);
            emptyTextLp.bottomMargin = dp(14);
            messageList.addView(emptyText, emptyTextLp);

            addQuickPrompt("试一下", "用三句话介绍你能帮我做什么。", "✦");
            addQuickPrompt("整理清单", "帮我把今天要做的事整理成清单。", "✓");
            addQuickPrompt("解释区别", "解释一下手机独立版和电脑本地版有什么区别。", "i");
            return;
        }

        for (Message message : conversation.messages) {
            addMessageView(message);
        }
        if (keepAtBottom) scrollToBottom();
    }

    private void addQuickPrompt(String label, String prompt, String mark) {
        LinearLayout button = new LinearLayout(this);
        button.setOrientation(LinearLayout.HORIZONTAL);
        button.setGravity(Gravity.CENTER_VERTICAL);
        button.setPadding(dp(14), 0, dp(12), 0);
        button.setBackground(makeStrokeBg(Color.rgb(255, 252, 246), Color.rgb(229, 221, 210), dp(16)));
        button.setClickable(true);
        button.setFocusable(true);
        addPressAnimation(button);
        button.setOnClickListener(v -> {
            promptInput.setText(prompt);
            submit();
        });

        TextView icon = new TextView(this);
        icon.setText(mark);
        icon.setGravity(Gravity.CENTER);
        icon.setTextColor(accentDark);
        icon.setTextSize(13);
        icon.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        icon.setBackground(makeSolidBg(Color.rgb(250, 239, 231), dp(10)));
        button.addView(icon, new LinearLayout.LayoutParams(dp(30), dp(30)));

        TextView title = new TextView(this);
        title.setText(label);
        title.setTextColor(text);
        title.setTextSize(14);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        titleLp.leftMargin = dp(12);
        button.addView(title, titleLp);

        TextView arrow = new TextView(this);
        arrow.setText("→");
        arrow.setTextColor(Color.rgb(159, 148, 137));
        arrow.setTextSize(18);
        arrow.setGravity(Gravity.CENTER);
        button.addView(arrow, new LinearLayout.LayoutParams(dp(22), LinearLayout.LayoutParams.MATCH_PARENT));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(58)
        );
        lp.topMargin = dp(9);
        messageList.addView(button, lp);
    }

    private void addMessageView(Message message) {
        boolean assistantRole = message.role.equals("assistant");

        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setGravity(assistantRole ? Gravity.LEFT : Gravity.RIGHT);

        TextView meta = new TextView(this);
        meta.setText(assistantRole ? safe(message.model, "Assistant") : "\u6211");
        meta.setTextColor(assistantRole ? Color.rgb(46, 65, 56) : accentDark);
        meta.setTextSize(11.5f);
        meta.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        meta.setGravity(assistantRole ? Gravity.LEFT : Gravity.RIGHT);
        meta.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
        meta.setSingleLine(true);
        meta.setEllipsize(TextUtils.TruncateAt.END);
        meta.setMaxWidth(Math.max(dp(96), getResources().getDisplayMetrics().widthPixels - dp(64)));
        meta.setPadding(dp(10), dp(2), dp(10), dp(4));
        meta.setBackground(makeSolidBg(
                assistantRole ? Color.rgb(246, 250, 246) : Color.rgb(255, 248, 244),
                dp(999)
        ));
        LinearLayout.LayoutParams metaLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        metaLp.leftMargin = dp(8);
        metaLp.rightMargin = dp(8);
        metaLp.bottomMargin = dp(8);
        wrap.addView(meta, metaLp);

        int maxBubbleWidth = maxMessageBubbleWidth();
        BoundedLinearLayout bubble = new BoundedLinearLayout(this, maxBubbleWidth);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setGravity(Gravity.NO_GRAVITY);
        bubble.setPadding(dp(14), dp(12), dp(14), dp(12));
        if (assistantRole && message.loading && message.content.trim().isEmpty()) {
            bubble.setMinimumWidth(dp(186));
        }
        bubble.setBackground(makeSolidBg(
                assistantRole ? sage : Color.rgb(255, 241, 233),
                dp(18)
        ));

        if (assistantRole) {
            LinearLayout loadingRow = new LinearLayout(this);
            loadingRow.setOrientation(LinearLayout.HORIZONTAL);
            loadingRow.setGravity(Gravity.CENTER_VERTICAL);
            loadingRow.setPadding(dp(10), 0, dp(10), 0);
            loadingRow.setMinimumWidth(dp(158));
            loadingRow.setTag(message.id + ":loadingBox");
            loadingRow.setVisibility(message.loading ? View.VISIBLE : View.GONE);
            loadingRow.setBackground(makeStrokeBg(Color.rgb(255, 248, 242), Color.rgb(230, 215, 205), dp(14)));

            TextView spinner = new TextView(this);
            spinner.setText("↻");
            spinner.setTextColor(accentDark);
            spinner.setTextSize(15);
            spinner.setGravity(Gravity.CENTER);
            loadingRow.addView(spinner, new LinearLayout.LayoutParams(dp(28), dp(34)));
            if (message.loading) startSpinner(spinner);

            TextView loadingText = new TextView(this);
            loadingText.setTag(message.id + ":loadingText");
            loadingText.setText(loadingText(message));
            loadingText.setTextColor(muted);
            loadingText.setTextSize(12);
            loadingText.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            loadingText.setSingleLine(true);
            loadingText.setEllipsize(TextUtils.TruncateAt.END);
            loadingRow.addView(loadingText, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));

            LinearLayout.LayoutParams loadingLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dp(36)
            );
            loadingLp.bottomMargin = dp(8);
            bubble.addView(loadingRow, loadingLp);

            LinearLayout reasoningBox = new LinearLayout(this);
            reasoningBox.setOrientation(LinearLayout.VERTICAL);
            reasoningBox.setTag(message.id + ":reasoningBox");
            reasoningBox.setVisibility(message.reasoningContent.trim().isEmpty() ? View.GONE : View.VISIBLE);

            Button reasoningToggle = flatButton(reasoningTitle(message));
            reasoningToggle.setTag(message.id + ":reasoningToggle");
            reasoningToggle.setTextSize(12.5f);
            reasoningToggle.setTextColor(accentDark);
            reasoningToggle.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
            reasoningToggle.setBackground(makeStrokeBg(Color.rgb(255, 248, 242), Color.rgb(230, 215, 205), dp(14)));
            reasoningToggle.setOnClickListener(v -> {
                message.reasoningOpen = !message.reasoningOpen;
                updateReasoningView(message, false);
                saveConversations();
            });
            LinearLayout.LayoutParams toggleLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(38)
            );
            toggleLp.topMargin = dp(8);
            reasoningBox.addView(reasoningToggle, toggleLp);

            TextView reasoningText = paragraph(message.reasoningContent);
            reasoningText.setTag(message.id + ":reasoningText");
            reasoningText.setTextColor(Color.rgb(120, 111, 99));
            reasoningText.setTextSize(12);
            reasoningText.setMaxWidth(maxBubbleWidth - dp(28));
            reasoningText.setLineSpacing(dp(2), 1.0f);
            reasoningText.setVisibility(message.reasoningOpen ? View.VISIBLE : View.GONE);
            enableCopy(reasoningText);
            LinearLayout.LayoutParams reasoningTextLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            reasoningTextLp.topMargin = dp(6);
            reasoningBox.addView(reasoningText, reasoningTextLp);

            bubble.addView(reasoningBox, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));
        }

        String visibleText = displayText(message.content);
        TextView content = paragraph("");
        content.setTextSize(assistantRole ? 15.4f : 15.0f);
        content.setMaxWidth(maxBubbleWidth - dp(28));
        content.setText(assistantRole ? renderMarkdownText(visibleText) : visibleText);
        content.setGravity(Gravity.LEFT);
        content.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
        content.setTag(message.id);
        boolean emptyLoadingAssistant = assistantRole
                && message.loading
                && visibleText.trim().isEmpty()
                && message.reasoningContent.trim().isEmpty();
        content.setVisibility(emptyLoadingAssistant ? View.GONE : View.VISIBLE);
        enableCopy(content);
        bubble.addView(content, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout media = new LinearLayout(this);
        media.setOrientation(LinearLayout.VERTICAL);
        media.setTag(message.id + ":media");
        populateMediaView(media, message.content);
        LinearLayout.LayoutParams mediaLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        mediaLp.topMargin = visibleText.trim().isEmpty() ? 0 : dp(10);
        bubble.addView(media, mediaLp);

        LinearLayout.LayoutParams bubbleLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        bubbleLp.gravity = assistantRole ? Gravity.LEFT : Gravity.RIGHT;
        wrap.addView(bubble, bubbleLp);
        addMessageActions(wrap, message, assistantRole);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lp.bottomMargin = dp(24);
        messageList.addView(wrap, lp);
        animateIn(wrap);
    }

    private void addMessageActions(LinearLayout wrap, Message message, boolean assistantRole) {
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(assistantRole ? Gravity.LEFT : Gravity.RIGHT);
        actions.setPadding(dp(8), dp(6), dp(8), 0);
        actions.setTag(message.id + ":actions");
        actions.setVisibility(message.loading ? View.GONE : View.VISIBLE);

        Button action = tinyActionButton(assistantRole ? "重新生成" : "编辑并重发");
        action.setOnClickListener(v -> {
            if (assistantRole) regenerateReply(message);
            else showEditAndResendDialog(message);
        });
        actions.addView(action, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(32)
        ));

        wrap.addView(actions, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
    }

    private TextView paragraph(String value) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(text);
        view.setTextSize(14.5f);
        view.setLineSpacing(dp(4), 1.0f);
        return view;
    }

    private int maxMessageBubbleWidth() {
        int screen = getResources().getDisplayMetrics().widthPixels;
        int comfortable = screen - dp(84);
        int proportional = (int) (screen * 0.82f);
        return Math.max(dp(180), Math.min(comfortable, proportional));
    }

    private CharSequence renderMarkdownText(String value) {
        SpannableStringBuilder out = new SpannableStringBuilder();
        String[] lines = safe(value, "").split("\\r?\\n");
        for (String rawLine : lines) {
            String lineValue = rawLine.trim();
            if (lineValue.isEmpty()) {
                if (out.length() > 0 && out.charAt(out.length() - 1) != '\n') out.append('\n');
                continue;
            }

            int start = out.length();
            if (lineValue.matches("^#{1,6}\\s+.+")) {
                appendInlineMarkdown(out, lineValue.replaceFirst("^#+\\s*", ""));
                out.setSpan(new AbsoluteSizeSpan(11, true), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                out.setSpan(new StyleSpan(Typeface.BOLD), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                out.setSpan(new ForegroundColorSpan(accentSoft), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (lineValue.matches("^[-*]\\s+.+")) {
                out.append("•  ");
                int itemStart = out.length();
                appendInlineMarkdown(out, lineValue.replaceFirst("^[-*]\\s+", ""));
                out.setSpan(new ForegroundColorSpan(accent), start, itemStart, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                appendInlineMarkdown(out, rawLine);
            }
            out.append('\n');
        }
        while (out.length() > 0 && out.charAt(out.length() - 1) == '\n') {
            out.delete(out.length() - 1, out.length());
        }
        return out;
    }

    private void appendInlineMarkdown(SpannableStringBuilder out, String value) {
        String remaining = safe(value, "");
        while (true) {
            int open = remaining.indexOf("**");
            if (open < 0) {
                out.append(remaining);
                return;
            }
            int close = remaining.indexOf("**", open + 2);
            if (close < 0) {
                out.append(remaining);
                return;
            }
            out.append(remaining.substring(0, open));
            int start = out.length();
            out.append(remaining.substring(open + 2, close));
            out.setSpan(new StyleSpan(Typeface.BOLD), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            remaining = remaining.substring(close + 2);
        }
    }

    private String displayText(String value) {
        String textValue = safe(value, "");
        String trimmed = textValue.trim();
        if ((trimmed.startsWith("{") || trimmed.startsWith("[")) && !extractImageRefs(trimmed).isEmpty()) {
            return "已生成图片";
        }
        textValue = MARKDOWN_IMAGE.matcher(textValue).replaceAll("");
        textValue = BARE_IMAGE.matcher(textValue).replaceAll("");
        return textValue.replaceAll("(?m)^\\s+$", "").trim();
    }

    private void populateMediaView(LinearLayout media, String content) {
        List<ImageRef> refs = extractImageRefs(content);
        String fingerprint = imageFingerprint(refs);
        Object previous = media.getContentDescription();
        if (fingerprint.equals(String.valueOf(previous))) return;

        media.setContentDescription(fingerprint);
        media.removeAllViews();
        media.setVisibility(refs.isEmpty() ? View.GONE : View.VISIBLE);
        if (refs.isEmpty()) return;

        for (ImageRef ref : refs) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(8), dp(8), dp(8), dp(8));
            card.setBackground(makeStrokeBg(Color.rgb(250, 246, 239), Color.rgb(222, 212, 199), dp(12)));

            ImageView image = new ImageView(this);
            image.setAdjustViewBounds(true);
            image.setMaxHeight(dp(360));
            image.setMinimumHeight(dp(150));
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setBackgroundColor(Color.rgb(246, 240, 233));
            card.addView(image, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));

            TextView status = new TextView(this);
            status.setText(ref.label.isEmpty() ? "正在加载图片..." : ref.label);
            status.setTextColor(muted);
            status.setTextSize(12);
            status.setSingleLine(true);
            status.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            statusLp.topMargin = dp(6);
            card.addView(status, statusLp);

            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            cardLp.bottomMargin = dp(8);
            media.addView(card, cardLp);
            loadImageInto(ref, image, status);
        }
    }

    private void loadImageInto(ImageRef ref, ImageView image, TextView status) {
        Bitmap cached;
        synchronized (imageCache) {
            cached = imageCache.get(ref.src);
        }
        if (cached != null) {
            showLoadedImage(ref, image, status, cached);
            return;
        }

        imageExecutor.execute(() -> {
            try {
                Bitmap bitmap = decodeImage(ref.src);
                synchronized (imageCache) {
                    imageCache.put(ref.src, bitmap);
                }
                mainHandler.post(() -> showLoadedImage(ref, image, status, bitmap));
            } catch (Exception error) {
                mainHandler.post(() -> {
                    status.setText("图片加载失败，长按文字可复制原始链接");
                    status.setTextColor(accentDark);
                });
            }
        });
    }

    private void showLoadedImage(ImageRef ref, ImageView image, TextView status, Bitmap bitmap) {
        image.setImageBitmap(bitmap);
        image.setOnClickListener(v -> showImagePreview(bitmap));
        status.setText(ref.label.isEmpty() ? "生成图片" : ref.label);
        if (autoFollowBottom) scrollToBottom();
    }

    private void showImagePreview(Bitmap bitmap) {
        LinearLayout panel = dialogPanel("图片预览");
        ImageView preview = new ImageView(this);
        preview.setImageBitmap(bitmap);
        preview.setAdjustViewBounds(true);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        panel.addView(preview, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        showCustomDialog(panel);
    }

    private Bitmap decodeImage(String src) throws Exception {
        if (src.startsWith("data:image/")) {
            int comma = src.indexOf(',');
            if (comma < 0) throw new IllegalArgumentException("Invalid data image");
            String base64 = src.substring(comma + 1).replaceAll("\\s+", "");
            byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (bitmap == null) throw new IllegalArgumentException("Unsupported image");
            return bitmap;
        }

        URL url = new URL(src);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(30000);
        try (InputStream input = conn.getInputStream()) {
            Bitmap bitmap = BitmapFactory.decodeStream(input);
            if (bitmap == null) throw new IllegalArgumentException("Unsupported image");
            return bitmap;
        } finally {
            conn.disconnect();
        }
    }

    private List<ImageRef> extractImageRefs(String content) {
        List<ImageRef> refs = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        String value = safe(content, "");

        Matcher markdown = MARKDOWN_IMAGE.matcher(value);
        while (markdown.find()) {
            addImageRef(refs, seen, markdown.group(2), markdown.group(1));
        }

        Matcher bare = BARE_IMAGE.matcher(value);
        while (bare.find()) {
            addImageRef(refs, seen, bare.group(1), "");
        }

        collectJsonImageRefs(value, refs, seen);
        return refs;
    }

    private void collectJsonImageRefs(String value, List<ImageRef> refs, Set<String> seen) {
        String clean = safe(value, "").trim();
        if (!(clean.startsWith("{") || clean.startsWith("["))) return;
        try {
            if (clean.startsWith("{")) collectJsonImageRefs(new JSONObject(clean), refs, seen);
            else collectJsonImageRefs(new JSONArray(clean), refs, seen);
        } catch (Exception ignored) {
        }
    }

    private void collectJsonImageRefs(Object node, List<ImageRef> refs, Set<String> seen) throws Exception {
        if (node instanceof JSONObject) {
            JSONObject obj = (JSONObject) node;
            Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                Object child = obj.opt(key);
                String lower = key.toLowerCase();
                if (child instanceof String) {
                    String value = (String) child;
                    if ("b64_json".equals(lower)) {
                        addImageRef(refs, seen, "data:image/png;base64," + value, "生成图片");
                    } else if (lower.contains("url") && value.startsWith("http")) {
                        addImageRef(refs, seen, value, "生成图片");
                    }
                }
                if (child instanceof JSONObject || child instanceof JSONArray) {
                    collectJsonImageRefs(child, refs, seen);
                }
            }
        } else if (node instanceof JSONArray) {
            JSONArray arr = (JSONArray) node;
            for (int i = 0; i < arr.length(); i++) {
                Object child = arr.opt(i);
                if (child instanceof JSONObject || child instanceof JSONArray) {
                    collectJsonImageRefs(child, refs, seen);
                }
            }
        }
    }

    private void addImageRef(List<ImageRef> refs, Set<String> seen, String src, String label) {
        String clean = safe(src, "").trim();
        if (clean.isEmpty() || seen.contains(clean)) return;
        seen.add(clean);
        refs.add(new ImageRef(clean, safe(label, "").trim()));
    }

    private String imageFingerprint(List<ImageRef> refs) {
        StringBuilder sb = new StringBuilder();
        for (ImageRef ref : refs) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(ref.src);
        }
        return sb.toString();
    }

    private void submit() {
        String prompt = promptInput.getText().toString().trim();
        if (prompt.isEmpty()) return;
        boolean imageRequest = imageModeEnabled;
        if ((imageRequest ? settings.imageApiKey : settings.apiKey).trim().isEmpty()) {
            showSettingsDialogSmart();
            toast("先填写 API Key");
            return;
        }

        Conversation conversation = activeConversation();
        if (isConversationGenerating(conversation.id)) {
            toast("当前对话正在回复中");
            return;
        }

        hideKeyboard();
        autoFollowBottom = true;
        RequestConfig requestConfig = RequestConfig.from(settings, conversation);
        if (conversation.messages.isEmpty()) conversation.title = makeTitle(prompt);
        imageRequest = shouldUseImageEndpoint(requestConfig);
        Message user = new Message(createId(), "user", prompt, null);
        conversation.messages.add(user);
        promptInput.setText("");
        appendAssistantAndGenerate(conversation, prompt, requestConfig, imageRequest);
    }

    private void appendAssistantAndGenerate(Conversation conversation, String prompt, RequestConfig requestConfig, boolean imageRequest) {
        if (isConversationGenerating(conversation.id)) {
            toast("当前对话正在回复中");
            return;
        }

        startGeneration(conversation.id);
        autoFollowBottom = true;
        Message assistant = new Message(createId(), "assistant", "", imageRequest ? imageGenerationModel(requestConfig) : requestConfig.model);
        assistant.loading = true;
        conversation.messages.add(assistant);
        conversation.updatedAt = System.currentTimeMillis();
        moveConversationToTop(rootConversationId(conversation));
        saveConversations();
        if (conversation.id.equals(activeConversationId)) renderMessages(true);

        if (imageRequest) {
            executor.execute(() -> requestImage(conversation, assistant, prompt, requestConfig));
        } else {
            executor.execute(() -> requestChat(conversation, assistant, requestConfig));
        }
    }

    private void regenerateReply(Message assistantMessage) {
        Conversation source = activeConversation();
        int assistantIndex = indexOfMessage(source, assistantMessage.id);
        if (assistantIndex < 0 || !"assistant".equals(assistantMessage.role)) return;
        if (isConversationGenerating(source.id)) {
            toast("当前对话正在回复中");
            return;
        }
        if (settings.apiKey.trim().isEmpty()) {
            showSettingsDialogSmart();
            toast("先填写 API Key");
            return;
        }

        int userIndex = previousUserIndex(source, assistantIndex);
        if (userIndex < 0) {
            toast("找不到对应的用户消息");
            return;
        }

        boolean wasLatestReply = assistantIndex == source.messages.size() - 1;
        boolean imageRequest = isImageGenerationModel(assistantMessage.model);
        Conversation target = source;
        int targetUserIndex = userIndex;
        if (!wasLatestReply) {
            target = createBranchCopy(source, userIndex, "重新生成");
            targetUserIndex = target.messages.size() - 1;
            activeConversationId = target.id;
            toast("已创建副本并重新生成");
        } else {
            removeMessagesAfter(source, userIndex);
        }

        Message user = target.messages.get(targetUserIndex);
        RequestConfig requestConfig = RequestConfig.from(settings, target);
        if (!imageRequest) imageRequest = shouldUseImageEndpoint(requestConfig);
        if ((imageRequest ? settings.imageApiKey : settings.apiKey).trim().isEmpty()) {
            showSettingsDialogSmart();
            toast("先填写 API Key");
            return;
        }
        saveConversations();
        appendAssistantAndGenerate(target, user.content, requestConfig, imageRequest);
    }

    private void showEditAndResendDialog(Message userMessage) {
        Conversation source = activeConversation();
        int messageIndex = indexOfMessage(source, userMessage.id);
        if (messageIndex < 0 || !"user".equals(userMessage.role)) return;
        if (isConversationGenerating(source.id)) {
            toast("当前对话正在回复中");
            return;
        }
        boolean imageRequest = imageModeEnabled;
        if ((imageRequest ? settings.imageApiKey : settings.apiKey).trim().isEmpty()) {
            showSettingsDialogSmart();
            toast("先填写 API Key");
            return;
        }

        LinearLayout panel = dialogPanel("编辑并重发");
        EditText input = field("消息内容", userMessage.content, false);
        input.setSingleLine(false);
        input.setMinLines(4);
        input.setMaxLines(8);
        input.setGravity(Gravity.TOP | Gravity.LEFT);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        panel.addView(label("消息内容"));
        panel.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        TextView hint = smallHint("会从这条消息开始重新发送。编辑较早消息时，会自动创建当前对话副本，原对话保持不变。");
        panel.addView(hint);

        LinearLayout actions = actionRow();
        Button cancel = outlineActionButton("取消");
        Button resend = filledButton("重新发送", accentDark, Color.WHITE);
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(0, dp(44), 1);
        cancelLp.rightMargin = dp(10);
        actions.addView(cancel, cancelLp);
        actions.addView(resend, new LinearLayout.LayoutParams(0, dp(44), 1));
        panel.addView(actions);

        AlertDialog dialog = showCustomDialog(panel);
        cancel.setOnClickListener(v -> dialog.dismiss());
        resend.setOnClickListener(v -> {
            String edited = input.getText().toString().trim();
            if (edited.isEmpty()) {
                toast("消息不能为空");
                return;
            }
            dialog.dismiss();
            resendEditedMessage(source, messageIndex, edited);
        });
    }

    private void resendEditedMessage(Conversation source, int messageIndex, String edited) {
        boolean latestUser = messageIndex == lastUserIndex(source);
        Conversation target = source;
        int targetUserIndex = messageIndex;
        if (!latestUser) {
            target = createBranchCopy(source, messageIndex, "编辑副本");
            targetUserIndex = target.messages.size() - 1;
            activeConversationId = target.id;
            toast("已创建副本并重新发送");
        }

        Message user = target.messages.get(targetUserIndex);
        user.content = edited;
        removeMessagesAfter(target, targetUserIndex);
        if (targetUserIndex == 0) target.title = makeTitle(edited);
        target.updatedAt = System.currentTimeMillis();

        RequestConfig requestConfig = RequestConfig.from(settings, target);
        boolean imageRequest = shouldUseImageEndpoint(requestConfig);
        saveConversations();
        appendAssistantAndGenerate(target, edited, requestConfig, imageRequest);
    }

    private void requestImage(Conversation conversation, Message assistant, String prompt, RequestConfig requestConfig) {
        try {
            if (isAnthropicProvider(requestConfig.imageProviderId, requestConfig.imageBaseUrl)) {
                throw new RuntimeException("Claude 来源不支持文生图输出，请切换到 OpenAI 或支持 /images/generations 的中转来源。");
            }
            URL url = new URL(trimSlash(requestConfig.imageBaseUrl) + "/images/generations");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(180000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + requestConfig.imageApiKey);

            JSONObject body = new JSONObject();
            String imageModel = imageGenerationModel(requestConfig);
            body.put("model", imageModel);
            body.put("prompt", prompt);
            body.put("n", 1);
            body.put("size", safe(requestConfig.imageSize, "1024x1024"));
            if (!isGptImageModel(imageModel)) {
                body.put("response_format", "url");
            }

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                String error = readAll(conn.getErrorStream());
                throw new RuntimeException(error.isEmpty() ? ("HTTP " + code) : error);
            }

            JSONObject payload = new JSONObject(readAll(conn.getInputStream()));
            String image = firstImageFromPayload(payload);
            if (image.isEmpty()) throw new RuntimeException("接口没有返回图片 URL 或 b64_json");
            assistant.content = "已生成图片\n" + image;
        } catch (Exception error) {
            assistant.content = "生图失败：" + readableError(error);
        } finally {
            assistant.loading = false;
            conversation.updatedAt = System.currentTimeMillis();
            mainHandler.post(() -> {
                updateMessageText(assistant);
                updateLoadingView(assistant);
                saveConversations();
                finishGeneration(conversation.id);
            });
        }
    }

    private String firstImageFromPayload(JSONObject payload) {
        JSONArray data = payload.optJSONArray("data");
        if (data != null) {
            for (int i = 0; i < data.length(); i++) {
                JSONObject item = data.optJSONObject(i);
                if (item == null) continue;
                String url = item.optString("url", "");
                if (!url.trim().isEmpty()) return url.trim();
                String b64 = item.optString("b64_json", "");
                if (!b64.trim().isEmpty()) return "data:image/png;base64," + b64.trim();
            }
        }
        String url = payload.optString("url", "");
        if (!url.trim().isEmpty()) return url.trim();
        String b64 = payload.optString("b64_json", "");
        if (!b64.trim().isEmpty()) return "data:image/png;base64," + b64.trim();
        return "";
    }

    private void requestChat(Conversation conversation, Message assistant, RequestConfig requestConfig) {
        if (isAnthropicProvider(requestConfig)) {
            requestAnthropicChat(conversation, assistant, requestConfig);
            return;
        }
        try {
            URL url = new URL(trimSlash(requestConfig.baseUrl) + "/chat/completions");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(120000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + requestConfig.apiKey);

            JSONObject body = new JSONObject();
            body.put("model", requestConfig.model);
            body.put("temperature", requestConfig.temperature);
            body.put("max_tokens", requestConfig.maxTokens);
            body.put("stream", true);
            if (supportsThinkingParameter(requestConfig)) {
                JSONObject thinking = new JSONObject();
                thinking.put("type", requestConfig.thinkingEnabled ? "enabled" : "disabled");
                body.put("thinking", thinking);
            }
            body.put("messages", buildMessages(conversation, requestConfig));

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                String error = readAll(conn.getErrorStream());
                throw new RuntimeException(error.isEmpty() ? ("HTTP " + code) : error);
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.startsWith("data:")) continue;
                    String data = line.substring(5).trim();
                    if ("[DONE]".equals(data)) break;
                    JSONObject chunk = new JSONObject(data);
                    JSONArray choices = chunk.optJSONArray("choices");
                    if (choices == null || choices.length() == 0) continue;
                    JSONObject delta = choices.getJSONObject(0).optJSONObject("delta");
                    if (delta == null) continue;

                    String reasoningToken = tokenFrom(delta, "reasoning_content");
                    if (!reasoningToken.isEmpty()) {
                        assistant.reasoningContent += reasoningToken;
                        mainHandler.post(() -> updateReasoningView(assistant));
                    }

                    String token = tokenFrom(delta, "content");
                    if (!token.isEmpty()) {
                        assistant.content += token;
                        mainHandler.post(() -> updateMessageText(assistant));
                    }
                }
            }
        } catch (Exception error) {
            assistant.content = "请求失败：" + readableError(error);
            assistant.loading = false;
            mainHandler.post(() -> updateMessageText(assistant));
        } finally {
            assistant.loading = false;
            conversation.updatedAt = System.currentTimeMillis();
            mainHandler.post(() -> {
                updateLoadingView(assistant);
                saveConversations();
                finishGeneration(conversation.id);
            });
        }
    }

    private void requestAnthropicChat(Conversation conversation, Message assistant, RequestConfig requestConfig) {
        try {
            URL url = new URL(trimSlash(requestConfig.baseUrl) + "/messages");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(120000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("x-api-key", requestConfig.apiKey);
            conn.setRequestProperty("anthropic-version", "2023-06-01");

            JSONObject body = new JSONObject();
            body.put("model", requestConfig.model);
            body.put("temperature", requestConfig.temperature);
            body.put("max_tokens", requestConfig.maxTokens);
            body.put("stream", true);
            if (!requestConfig.systemPrompt.trim().isEmpty()) {
                body.put("system", requestConfig.systemPrompt.trim());
            }
            body.put("messages", buildAnthropicMessages(conversation));

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                String error = readAll(conn.getErrorStream());
                throw new RuntimeException(error.isEmpty() ? ("HTTP " + code) : error);
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.startsWith("data:")) continue;
                    String data = line.substring(5).trim();
                    if ("[DONE]".equals(data)) break;
                    JSONObject chunk = new JSONObject(data);
                    if ("error".equals(chunk.optString("type"))) {
                        JSONObject error = chunk.optJSONObject("error");
                        throw new RuntimeException(error != null ? error.optString("message", data) : data);
                    }
                    JSONObject delta = chunk.optJSONObject("delta");
                    if (delta == null) continue;

                    String reasoningToken = tokenFrom(delta, "thinking");
                    if (!reasoningToken.isEmpty()) {
                        assistant.reasoningContent += reasoningToken;
                        mainHandler.post(() -> updateReasoningView(assistant));
                    }

                    String token = tokenFrom(delta, "text");
                    if (!token.isEmpty()) {
                        assistant.content += token;
                        mainHandler.post(() -> updateMessageText(assistant));
                    }
                }
            }
        } catch (Exception error) {
            assistant.content = "请求失败：" + readableError(error);
            assistant.loading = false;
            mainHandler.post(() -> updateMessageText(assistant));
        } finally {
            assistant.loading = false;
            conversation.updatedAt = System.currentTimeMillis();
            mainHandler.post(() -> {
                updateLoadingView(assistant);
                saveConversations();
                finishGeneration(conversation.id);
            });
        }
    }

    private JSONArray buildMessages(Conversation conversation, RequestConfig requestConfig) throws Exception {
        JSONArray arr = new JSONArray();
        if (!requestConfig.systemPrompt.trim().isEmpty()) {
            JSONObject system = new JSONObject();
            system.put("role", "system");
            system.put("content", requestConfig.systemPrompt.trim());
            arr.put(system);
        }
        for (Message message : conversation.messages) {
            String content = chatHistoryContent(message.content);
            if (content.trim().isEmpty()) continue;
            JSONObject item = new JSONObject();
            item.put("role", message.role);
            item.put("content", content);
            arr.put(item);
        }
        return arr;
    }

    private JSONArray buildAnthropicMessages(Conversation conversation) throws Exception {
        JSONArray arr = new JSONArray();
        for (Message message : conversation.messages) {
            if (!"user".equals(message.role) && !"assistant".equals(message.role)) continue;
            String content = chatHistoryContent(message.content);
            if (content.trim().isEmpty()) continue;
            JSONObject item = new JSONObject();
            item.put("role", message.role);
            item.put("content", content);
            arr.put(item);
        }
        return arr;
    }

    private String chatHistoryContent(String content) {
        String value = safe(content, "");
        if (extractImageRefs(value).isEmpty()) return value;
        String textOnly = displayText(value).trim();
        return textOnly.isEmpty() ? "已生成图片" : textOnly;
    }

    private void updateMessageText(Message message) {
        CharSequence textValue = message.role.equals("assistant")
                ? renderMarkdownText(displayText(message.content))
                : displayText(message.content);
        if (findAndSetText(messageList, message.id, textValue)) {
            updateMediaView(message);
            updateLoadingView(message);
            if (autoFollowBottom) scrollToBottom();
        }
    }

    private void updateLoadingView(Message message) {
        boolean emptyLoadingAssistant = "assistant".equals(message.role)
                && message.loading
                && displayText(message.content).trim().isEmpty()
                && message.reasoningContent.trim().isEmpty();
        findAndSetVisibility(messageList, message.id, emptyLoadingAssistant ? View.GONE : View.VISIBLE);
        findAndSetVisibility(messageList, message.id + ":loadingBox", message.loading ? View.VISIBLE : View.GONE);
        findAndSetVisibility(messageList, message.id + ":actions", message.loading ? View.GONE : View.VISIBLE);
        findAndSetText(messageList, message.id + ":loadingText", loadingText(message));
    }

    private String loadingText(Message message) {
        if (!message.content.trim().isEmpty() || !message.reasoningContent.trim().isEmpty()) {
            return "正在生成回复...";
        }
        return "正在连接模型...";
    }

    private void updateReasoningView(Message message) {
        updateReasoningView(message, true);
    }

    private void updateReasoningView(Message message, boolean keepAtBottom) {
        boolean changed = false;
        changed |= findAndSetVisibility(messageList, message.id + ":reasoningBox", message.reasoningContent.trim().isEmpty() ? View.GONE : View.VISIBLE);
        changed |= findAndSetText(messageList, message.id + ":reasoningToggle", reasoningTitle(message));
        changed |= findAndSetText(messageList, message.id + ":reasoningText", message.reasoningContent);
        changed |= findAndSetVisibility(messageList, message.id + ":reasoningText", message.reasoningOpen ? View.VISIBLE : View.GONE);
        if (changed) updateLoadingView(message);
        if (changed && keepAtBottom && autoFollowBottom) scrollToBottom();
    }

    private void updateMediaView(Message message) {
        findAndPopulateMedia(messageList, message.id + ":media", message.content);
    }

    private String reasoningTitle(Message message) {
        return (message.reasoningOpen ? "-  " : "+  ") + "\u601d\u8003\u8fc7\u7a0b";
    }

    private boolean findAndSetText(LinearLayout parent, String tag, CharSequence content) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (tag.equals(child.getTag()) && child instanceof TextView) {
                ((TextView) child).setText(content);
                return true;
            }
            if (child instanceof LinearLayout && findAndSetText((LinearLayout) child, tag, content)) {
                return true;
            }
        }
        return false;
    }

    private boolean findAndPopulateMedia(LinearLayout parent, String tag, String content) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (tag.equals(child.getTag()) && child instanceof LinearLayout) {
                populateMediaView((LinearLayout) child, content);
                return true;
            }
            if (child instanceof LinearLayout && findAndPopulateMedia((LinearLayout) child, tag, content)) {
                return true;
            }
        }
        return false;
    }

    private boolean findAndSetVisibility(LinearLayout parent, String tag, int visibility) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (tag.equals(child.getTag())) {
                child.setVisibility(visibility);
                return true;
            }
            if (child instanceof LinearLayout && findAndSetVisibility((LinearLayout) child, tag, visibility)) {
                return true;
            }
        }
        return false;
    }

    private android.graphics.drawable.Drawable historyItemBg(String conversationId) {
        return makeStrokeBg(
                conversationId.equals(activeConversationId) ? Color.rgb(248, 239, 226) : Color.rgb(250, 246, 239),
                line,
                dp(12)
        );
    }

    private void refreshHistorySelection(LinearLayout historyList) {
        for (int i = 0; i < historyList.getChildCount(); i++) {
            View child = historyList.getChildAt(i);
            if (!(child instanceof LinearLayout)) continue;
            Object id = child.getTag();
            if (id == null) continue;
            LinearLayout row = (LinearLayout) child;
            if (row.getChildCount() > 0 && row.getChildAt(0) instanceof Button) {
                boolean selected = String.valueOf(id).equals(activeConversationId);
                Button button = (Button) row.getChildAt(0);
                button.setText(menuRowText(button.getText().toString().replaceFirst("^[●◦]\\s*", "").replaceFirst("\\s*›$", ""), selected ? "●" : "◦", "›"));
                button.setBackground(menuRowBg(selected));
            }
        }
    }

    private void showHistoryDialog() {
        LinearLayout panel = dialogPanel("历史对话");
        final AlertDialog[] dialogRef = new AlertDialog[1];
        Button newChat = filledButton("新对话", sage, Color.rgb(54, 72, 63));
        panel.addView(newChat, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));

        ScrollView historyScroll = new ScrollView(this);
        historyScroll.setFillViewport(false);
        LinearLayout historyList = new LinearLayout(this);
        historyList.setOrientation(LinearLayout.VERTICAL);
        historyList.setLayoutTransition(new LayoutTransition());
        historyScroll.addView(historyList);

        Runnable[] renderHistory = new Runnable[1];
        renderHistory[0] = () -> {
            historyList.removeAllViews();
            for (Conversation conversation : conversations) {
                if (isBranchConversation(conversation)) continue;
                addHistoryRow(historyList, conversation, false, dialogRef, renderHistory[0]);
                if (conversation.branchesOpen) {
                    for (Conversation branch : conversations) {
                        if (conversation.id.equals(branch.parentId)) {
                            addHistoryRow(historyList, branch, true, dialogRef, renderHistory[0]);
                        }
                    }
                }
            }
        };
        renderHistory[0].run();

        LinearLayout.LayoutParams historyLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                conversations.size() > 6 ? dp(420) : LinearLayout.LayoutParams.WRAP_CONTENT
        );
        historyLp.topMargin = dp(8);
        panel.addView(historyScroll, historyLp);

        LinearLayout actions = actionRow();
        Button close = outlineActionButton("关闭");
        actions.addView(close, new LinearLayout.LayoutParams(dp(104), dp(44)));
        panel.addView(actions);
        AlertDialog dialog = showCustomDialog(panel);
        dialogRef[0] = dialog;
        newChat.setOnClickListener(v -> {
            createConversation(true);
            dialog.dismiss();
        });
        close.setOnClickListener(v -> dialog.dismiss());
    }

    private void addHistoryRow(LinearLayout historyList, Conversation conversation, boolean branch, AlertDialog[] dialogRef, Runnable refresh) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(branch ? dp(28) : 0, branch ? dp(5) : dp(8), 0, 0);
        row.setTag(conversation.id);

        List<Conversation> branches = branchChildren(conversation.id);
        boolean selected = conversation.id.equals(activeConversationId);
        Button open = menuRowButton(historyTitle(conversation, branch), selected ? "●" : "◦", branch ? "副本" : "›", selected);
        int rowHeight = branch ? dp(36) : dp(44);
        if (branch) {
            open.setTextSize(12.2f);
            open.setTextColor(selected ? accentDark : muted);
            open.setPadding(dp(10), 0, dp(10), 0);
            open.setBackground(makeStrokeBg(
                    selected ? Color.rgb(249, 240, 229) : Color.rgb(253, 249, 244),
                    selected ? Color.rgb(226, 209, 190) : Color.rgb(235, 228, 218),
                    dp(12)
            ));
        } else if (!branches.isEmpty()) {
            open.setPadding(dp(12), 0, dp(40), 0);
        }

        android.widget.FrameLayout openFrame = new android.widget.FrameLayout(this);
        openFrame.addView(open, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
        ));
        if (!branch && !branches.isEmpty()) {
            Button toggle = historyToggleButton(conversation.branchesOpen ? "▾" : "▸");
            toggle.setContentDescription("展开副本");
            toggle.setOnClickListener(v -> {
                conversation.branchesOpen = !conversation.branchesOpen;
                saveConversations();
                refresh.run();
            });
            android.widget.FrameLayout.LayoutParams toggleLp = new android.widget.FrameLayout.LayoutParams(dp(32), dp(34));
            toggleLp.gravity = Gravity.RIGHT | Gravity.CENTER_VERTICAL;
            toggleLp.rightMargin = dp(4);
            openFrame.addView(toggle, toggleLp);
        }
        row.addView(openFrame, new LinearLayout.LayoutParams(0, rowHeight, 1));

        Button delete = iconButton("×");
        if (branch) delete.setTextSize(15);
        LinearLayout.LayoutParams deleteLp = new LinearLayout.LayoutParams(branch ? dp(34) : dp(42), branch ? dp(34) : dp(42));
        deleteLp.leftMargin = dp(8);
        row.addView(delete, deleteLp);
        historyList.addView(row);

        open.setOnClickListener(v -> {
            activeConversationId = conversation.id;
            saveConversations();
            renderMessages();
            refreshBusyState();
            if (dialogRef[0] != null) dialogRef[0].dismiss();
        });

        open.setOnLongClickListener(v -> {
            showRenameConversationDialog(conversation, () -> {
                if (conversation.id.equals(activeConversationId) && titleView != null) {
                    titleView.setText(conversation.title);
                }
                saveConversations();
                refresh.run();
            });
            return true;
        });

        delete.setOnClickListener(v -> {
            boolean activeRemoved = removeConversationWithBranches(conversation);
            if (conversations.isEmpty()) createConversation(false);
            if (activeRemoved) {
                activeConversationId = conversations.get(0).id;
                renderMessages();
                refreshBusyState();
            }
            saveConversations();
            refresh.run();
        });
    }

    private String historyTitle(Conversation conversation, boolean branch) {
        return branch ? "↳  " + conversation.title : conversation.title;
    }

    private Button historyToggleButton(String value) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(value);
        button.setTextSize(13);
        button.setTextColor(accentDark);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setPadding(0, 0, 0, 0);
        button.setStateListAnimator(null);
        button.setBackground(makeSolidBg(Color.rgb(255, 248, 242), dp(999)));
        addPressAnimation(button);
        return button;
    }

    private List<Conversation> branchChildren(String parentId) {
        List<Conversation> result = new ArrayList<>();
        for (Conversation conversation : conversations) {
            if (parentId.equals(conversation.parentId)) result.add(conversation);
        }
        return result;
    }

    private boolean removeConversationWithBranches(Conversation target) {
        String targetRoot = rootConversationId(target);
        boolean activeRemoved = false;
        for (int i = conversations.size() - 1; i >= 0; i--) {
            Conversation item = conversations.get(i);
            boolean remove = isBranchConversation(target)
                    ? item.id.equals(target.id)
                    : item.id.equals(target.id) || targetRoot.equals(item.parentId);
            if (remove) {
                if (item.id.equals(activeConversationId)) activeRemoved = true;
                conversations.remove(i);
            }
        }
        return activeRemoved;
    }

    private void showModelDialog() {
        showModelDialog(model -> {
            ProviderConfig active = settings.activeConfig();
            active.model = model;
            settings.model = model;
            settings.save(prefs);
            updateModelButton();
            toast("已切换到 " + settings.model);
        }, settings.providerId, settings.modelOptions, settings.model);
    }

    private void showModelDialog(ModelPicked callback) {
        showModelDialog(callback, settings.providerId, settings.modelOptions, settings.model);
    }

    private void showModelDialog(ModelPicked callback, String providerId, String modelOptions) {
        showModelDialog(callback, providerId, modelOptions, settings.model);
    }

    private void showModelDialog(ModelPicked callback, String providerId, String modelOptions, String currentModel) {
        List<String> models = availableModels(providerId, modelOptions, currentModel);
        int selected = 0;
        for (int i = 0; i < models.size(); i++) {
            if (models.get(i).equals(currentModel)) selected = i;
        }

        LinearLayout panel = dialogPanel("切换模型");
        ScrollView modelScroll = new ScrollView(this);
        modelScroll.setFillViewport(false);
        LinearLayout modelList = new LinearLayout(this);
        modelList.setOrientation(LinearLayout.VERTICAL);
        modelScroll.addView(modelList);
        List<Button> rows = new ArrayList<>();
        final AlertDialog[] dialogRef = new AlertDialog[1];

        for (int i = 0; i < models.size(); i++) {
            String model = models.get(i);
            Button row = modelRow(model, i == selected);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(44)
            );
            lp.bottomMargin = dp(8);
            modelList.addView(row, lp);
            rows.add(row);
            row.setOnClickListener(v -> {
                callback.pick(model);
                if (dialogRef[0] != null) dialogRef[0].dismiss();
            });
        }

        LinearLayout.LayoutParams modelScrollLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                models.size() > 7 ? dp(430) : LinearLayout.LayoutParams.WRAP_CONTENT
        );
        modelScrollLp.topMargin = dp(2);
        panel.addView(modelScroll, modelScrollLp);

        LinearLayout actions = actionRow();
        Button cancel = outlineActionButton("取消");
        actions.addView(cancel, new LinearLayout.LayoutParams(dp(104), dp(44)));
        panel.addView(actions);
        AlertDialog dialog = showCustomDialog(panel);
        dialogRef[0] = dialog;
        cancel.setOnClickListener(v -> dialog.dismiss());
    }

    private void showImageModelDialog(ModelPicked callback, String providerId, String modelOptions, String currentModel) {
        List<String> models = availableImageModels(providerId, modelOptions, currentModel);
        int selected = 0;
        for (int i = 0; i < models.size(); i++) {
            if (models.get(i).equals(currentModel)) selected = i;
        }

        LinearLayout panel = dialogPanel("文生图模型");
        ScrollView modelScroll = new ScrollView(this);
        modelScroll.setFillViewport(false);
        LinearLayout modelList = new LinearLayout(this);
        modelList.setOrientation(LinearLayout.VERTICAL);
        modelScroll.addView(modelList);
        final AlertDialog[] dialogRef = new AlertDialog[1];

        for (int i = 0; i < models.size(); i++) {
            String model = models.get(i);
            Button row = modelRow(model, i == selected);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(44)
            );
            lp.bottomMargin = dp(8);
            modelList.addView(row, lp);
            row.setOnClickListener(v -> {
                callback.pick(model);
                if (dialogRef[0] != null) dialogRef[0].dismiss();
            });
        }

        LinearLayout.LayoutParams modelScrollLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                models.size() > 7 ? dp(430) : LinearLayout.LayoutParams.WRAP_CONTENT
        );
        modelScrollLp.topMargin = dp(2);
        panel.addView(modelScroll, modelScrollLp);

        LinearLayout actions = actionRow();
        Button cancel = outlineActionButton("取消");
        actions.addView(cancel, new LinearLayout.LayoutParams(dp(104), dp(44)));
        panel.addView(actions);
        AlertDialog dialog = showCustomDialog(panel);
        dialogRef[0] = dialog;
        cancel.setOnClickListener(v -> dialog.dismiss());
    }

    private void showSettingsDialogSmart() {
        LinearLayout form = dialogPanel("设置");

        Conversation conversationForSettings = activeConversation();
        final String[] pickedProvider = {settings.providerId};
        ProviderConfig initialConfig = settings.configFor(pickedProvider[0]);
        final String[] pickedModel = {defaultModelFor(initialConfig)};
        final String[] pickedModelOptions = {initialConfig.modelOptions};
        final String[] pickedImageProvider = {settings.imageProviderId};
        ProviderConfig initialImageConfig = settings.configFor(pickedImageProvider[0]);
        final String[] pickedImageModelOptions = {safe(settings.imageModelOptions, initialImageConfig.modelOptions)};
        final String[] pickedImageModel = {defaultImageModelFor(pickedImageProvider[0], pickedImageModelOptions[0], settings.imageModel)};

        Button providerPicker = flatButton(sourceName(initialConfig));
        providerPicker.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        providerPicker.setBackground(makeStrokeBg(Color.rgb(250, 246, 239), line, dp(12)));

        EditText baseUrl = field("Base URL", initialConfig.baseUrl, false);
        EditText apiKey = field("API Key", initialConfig.apiKey, false);

        Button imageProviderPicker = flatButton(sourceName(initialImageConfig));
        imageProviderPicker.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        imageProviderPicker.setBackground(makeStrokeBg(Color.rgb(250, 246, 239), line, dp(12)));

        EditText imageBaseUrl = field("Base URL", safe(settings.imageBaseUrl, initialImageConfig.baseUrl), false);
        EditText imageApiKey = field("API Key", safe(settings.imageApiKey, initialImageConfig.apiKey), false);

        Button modelPicker = flatButton(pickedModel[0]);
        modelPicker.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        modelPicker.setBackground(makeStrokeBg(Color.rgb(250, 246, 239), line, dp(12)));
        modelPicker.setOnClickListener(v -> showModelDialog(model -> {
            pickedModel[0] = model;
            modelPicker.setText(model);
        }, pickedProvider[0], pickedModelOptions[0], pickedModel[0]));

        Button imageModelPicker = flatButton(pickedImageModel[0]);
        imageModelPicker.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        imageModelPicker.setBackground(makeStrokeBg(Color.rgb(250, 246, 239), line, dp(12)));
        imageModelPicker.setOnClickListener(v -> showImageModelDialog(model -> {
            pickedImageModel[0] = model;
            imageModelPicker.setText(model);
        }, pickedImageProvider[0], pickedImageModelOptions[0], pickedImageModel[0]));

        providerPicker.setOnClickListener(v -> showSourceDialog(pickedProvider[0], config -> {
            saveProviderDraft(pickedProvider[0], baseUrl, apiKey, pickedModel[0], pickedModelOptions[0]);
            pickedProvider[0] = config.providerId;
            providerPicker.setText(sourceName(config));
            baseUrl.setText(config.baseUrl);
            apiKey.setText(config.apiKey);
            pickedModelOptions[0] = config.modelOptions;
            pickedModel[0] = defaultModelFor(config);
            modelPicker.setText(pickedModel[0]);
        }));

        imageProviderPicker.setOnClickListener(v -> showSourceDialog(pickedImageProvider[0], config -> {
            saveImageProviderDraft(pickedImageProvider[0], imageBaseUrl, imageApiKey, pickedImageModelOptions[0]);
            pickedImageProvider[0] = config.providerId;
            imageProviderPicker.setText(sourceName(config));
            imageBaseUrl.setText(config.baseUrl);
            imageApiKey.setText(config.apiKey);
            pickedImageModelOptions[0] = config.modelOptions;
            pickedImageModel[0] = defaultImageModelFor(config.providerId, config.modelOptions, pickedImageModel[0]);
            imageModelPicker.setText(pickedImageModel[0]);
        }));

        EditText temperature = field("Temperature", String.valueOf(settings.temperature), false);
        EditText maxTokens = field("Max tokens", String.valueOf(settings.maxTokens), false);
        EditText imageSize = field("Image Size", settings.imageSize, false);
        EditText systemPrompt = field("System Prompt", conversationSystemPrompt(conversationForSettings), false);
        systemPrompt.setMinLines(2);

        LinearLayout mainPage = new LinearLayout(this);
        mainPage.setOrientation(LinearLayout.VERTICAL);
        LinearLayout imagePage = new LinearLayout(this);
        imagePage.setOrientation(LinearLayout.VERTICAL);
        imagePage.setVisibility(View.GONE);

        mainPage.addView(label("服务商"));
        mainPage.addView(providerPicker, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));
        mainPage.addView(label("Base URL"));
        mainPage.addView(baseUrl);
        mainPage.addView(label("API Key"));
        mainPage.addView(apiKey);
        mainPage.addView(label("对话模型"));
        mainPage.addView(modelPicker, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));
        TextView modelHint = smallHint("填好 Key 后可获取模型；对话和生图服务商互不绑定。");
        mainPage.addView(modelHint);
        Button fetchModels = outlineActionButton("获取模型");
        LinearLayout.LayoutParams fetchLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44));
        fetchLp.topMargin = dp(8);
        mainPage.addView(fetchModels, fetchLp);
        mainPage.addView(label("Temperature"));
        mainPage.addView(temperature);
        mainPage.addView(label("Max tokens"));
        mainPage.addView(maxTokens);
        mainPage.addView(label("当前对话 System Prompt"));
        mainPage.addView(systemPrompt);

        imagePage.addView(label("生图服务商"));
        imagePage.addView(imageProviderPicker, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));
        imagePage.addView(label("Base URL"));
        imagePage.addView(imageBaseUrl);
        imagePage.addView(label("API Key"));
        imagePage.addView(imageApiKey);
        imagePage.addView(label("文生图模型"));
        imagePage.addView(imageModelPicker, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));
        TextView imageHint = smallHint("底部“图”按钮开启时调用这里的服务商，地址为 Base URL + /images/generations。");
        imagePage.addView(imageHint);
        Button fetchImageModels = outlineActionButton("获取生图模型");
        LinearLayout.LayoutParams fetchImageLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44));
        fetchImageLp.topMargin = dp(8);
        imagePage.addView(fetchImageModels, fetchImageLp);
        imagePage.addView(label("图片尺寸"));
        imagePage.addView(imageSize);

        form.addView(mainPage);
        form.addView(imagePage);

        Button imageConfigBubble = flatButton("生图配置");
        imageConfigBubble.setTextSize(13);
        imageConfigBubble.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        imageConfigBubble.setTextColor(accentDark);
        imageConfigBubble.setBackground(makeStrokeBg(Color.rgb(255, 248, 242), Color.rgb(226, 209, 190), dp(999)));
        LinearLayout.LayoutParams bubbleLp = new LinearLayout.LayoutParams(dp(132), dp(42));
        bubbleLp.gravity = Gravity.RIGHT;
        bubbleLp.topMargin = dp(12);
        bubbleLp.bottomMargin = dp(2);
        form.addView(imageConfigBubble, bubbleLp);
        final boolean[] showingImageSettings = {false};
        imageConfigBubble.setOnClickListener(v -> {
            showingImageSettings[0] = !showingImageSettings[0];
            mainPage.setVisibility(showingImageSettings[0] ? View.GONE : View.VISIBLE);
            imagePage.setVisibility(showingImageSettings[0] ? View.VISIBLE : View.GONE);
            imageConfigBubble.setText(showingImageSettings[0] ? "返回主设置" : "生图配置");
            imageConfigBubble.setTextColor(showingImageSettings[0] ? Color.WHITE : accentDark);
            imageConfigBubble.setBackground(showingImageSettings[0]
                    ? makeSolidBg(accent, dp(999))
                    : makeStrokeBg(Color.rgb(255, 248, 242), Color.rgb(226, 209, 190), dp(999)));
        });

        LinearLayout actions = actionRow();
        Button cancel = outlineActionButton("取消");
        Button save = filledButton("保存", accentDark, Color.WHITE);
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(0, dp(46), 1);
        cancelLp.rightMargin = dp(10);
        actions.addView(cancel, cancelLp);
        actions.addView(save, new LinearLayout.LayoutParams(0, dp(46), 1));
        form.addView(actions, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        AlertDialog dialog = showCustomDialog(form);
        cancel.setOnClickListener(v -> dialog.dismiss());
        fetchModels.setOnClickListener(v -> {
            fetchModels.setEnabled(false);
            modelHint.setText("正在获取模型列表...");
            executor.execute(() -> {
                try {
                    List<String> fetched = fetchModelList(pickedProvider[0], baseUrl.getText().toString().trim(), apiKey.getText().toString().trim());
                    if (fetched.isEmpty()) throw new RuntimeException("没有返回可用模型");
                    String joined = joinModels(fetched);
                    mainHandler.post(() -> {
                        pickedModelOptions[0] = joined;
                        List<String> chatModels = availableModels(pickedProvider[0], pickedModelOptions[0], pickedModel[0]);
                        if (!chatModels.contains(pickedModel[0]) && !chatModels.isEmpty()) {
                            pickedModel[0] = chatModels.get(0);
                            modelPicker.setText(pickedModel[0]);
                        }
                        modelHint.setText("已获取 " + fetched.size() + " 个对话模型。");
                        fetchModels.setEnabled(true);
                    });
                } catch (Exception error) {
                    mainHandler.post(() -> {
                        modelHint.setText("获取失败：" + readableError(error));
                        fetchModels.setEnabled(true);
                    });
                }
            });
        });
        fetchImageModels.setOnClickListener(v -> {
            fetchImageModels.setEnabled(false);
            imageHint.setText("正在获取生图模型列表...");
            executor.execute(() -> {
                try {
                    List<String> fetched = fetchModelList(pickedImageProvider[0], imageBaseUrl.getText().toString().trim(), imageApiKey.getText().toString().trim());
                    if (fetched.isEmpty()) throw new RuntimeException("没有返回可用模型");
                    String joined = joinModels(fetched);
                    mainHandler.post(() -> {
                        pickedImageModelOptions[0] = joined;
                        List<String> imageModels = availableImageModels(pickedImageProvider[0], pickedImageModelOptions[0], pickedImageModel[0]);
                        if (!imageModels.contains(pickedImageModel[0]) && !imageModels.isEmpty()) {
                            pickedImageModel[0] = imageModels.get(0);
                            imageModelPicker.setText(pickedImageModel[0]);
                        }
                        imageHint.setText("已获取 " + fetched.size() + " 个模型，可点文生图模型选择。");
                        fetchImageModels.setEnabled(true);
                    });
                } catch (Exception error) {
                    mainHandler.post(() -> {
                        imageHint.setText("获取失败：" + readableError(error));
                        fetchImageModels.setEnabled(true);
                    });
                }
            });
        });
        save.setOnClickListener(v -> {
            settings.providerId = pickedProvider[0];
            saveProviderDraft(pickedProvider[0], baseUrl, apiKey, pickedModel[0], pickedModelOptions[0]);
            settings.applyActiveProvider();
            settings.imageProviderId = pickedImageProvider[0];
            saveImageProviderDraft(pickedImageProvider[0], imageBaseUrl, imageApiKey, pickedImageModelOptions[0]);
            settings.applyImageProvider();
            settings.temperature = parseDouble(temperature.getText().toString(), 0.7);
            settings.maxTokens = parseInt(maxTokens.getText().toString(), 2048);
            settings.imageModel = safe(pickedImageModel[0].trim(), "gpt-image-1");
            settings.imageSize = safe(imageSize.getText().toString().trim(), "1024x1024");
            Conversation active = activeConversation();
            active.systemPrompt = systemPrompt.getText().toString();
            settings.save(prefs);
            saveConversations();
            updateModelButton();
            toast("已保存");
            dialog.dismiss();
        });
    }

    private String conversationSystemPrompt(Conversation conversation) {
        if (conversation != null && conversation.systemPrompt != null) return conversation.systemPrompt;
        return Settings.DEFAULT_SYSTEM_PROMPT;
    }

    private void showSettingsDialog() {
        LinearLayout form = dialogPanel("设置");

        EditText baseUrl = field("Base URL", settings.baseUrl, false);
        EditText apiKey = field("API Key", settings.apiKey, true);
        final String[] pickedModel = {settings.model};
        Button modelPicker = flatButton(settings.model);
        modelPicker.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        modelPicker.setBackground(makeStrokeBg(Color.rgb(250, 246, 239), line, dp(12)));
        modelPicker.setOnClickListener(v -> showModelDialog(model -> {
            pickedModel[0] = model;
            modelPicker.setText(model);
        }, settings.providerId, settings.modelOptions, pickedModel[0]));
        EditText temperature = field("Temperature", String.valueOf(settings.temperature), false);
        EditText maxTokens = field("Max tokens", String.valueOf(settings.maxTokens), false);
        EditText systemPrompt = field("System Prompt", settings.systemPrompt, false);
        systemPrompt.setMinLines(3);

        form.addView(label("Base URL"));
        form.addView(baseUrl);
        form.addView(label("API Key"));
        form.addView(apiKey);
        form.addView(label("模型"));
        form.addView(modelPicker, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));
        form.addView(label("Temperature"));
        form.addView(temperature);
        form.addView(label("Max tokens"));
        form.addView(maxTokens);
        form.addView(label("System Prompt"));
        form.addView(systemPrompt);

        LinearLayout actions = actionRow();
        Button cancel = outlineActionButton("取消");
        Button save = filledButton("保存", accentDark, Color.WHITE);
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(0, dp(46), 1);
        cancelLp.rightMargin = dp(10);
        actions.addView(cancel, cancelLp);
        actions.addView(save, new LinearLayout.LayoutParams(0, dp(46), 1));
        form.addView(actions, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        AlertDialog dialog = showCustomDialog(form);
        cancel.setOnClickListener(v -> dialog.dismiss());
        save.setOnClickListener(v -> {
            settings.baseUrl = baseUrl.getText().toString().trim();
            settings.apiKey = apiKey.getText().toString().trim();
            settings.model = pickedModel[0];
            settings.temperature = parseDouble(temperature.getText().toString(), 0.7);
            settings.maxTokens = parseInt(maxTokens.getText().toString(), 2048);
            settings.systemPrompt = systemPrompt.getText().toString();
            settings.save(prefs);
            updateModelButton();
            toast("已保存");
            dialog.dismiss();
        });
    }

    private TextView label(String value) {
        TextView label = new TextView(this);
        label.setText(value);
        label.setTextColor(muted);
        label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        label.setTextSize(10.5f);
        label.setPadding(0, dp(12), 0, dp(5));
        return label;
    }

    private TextView smallHint(String value) {
        TextView hint = new TextView(this);
        hint.setText(value);
        hint.setTextColor(muted);
        hint.setTextSize(10.8f);
        hint.setLineSpacing(dp(3), 1.0f);
        hint.setPadding(dp(2), dp(6), dp(2), dp(2));
        return hint;
    }

    private void showSourceDialog(String selectedProviderId, SourcePicked callback) {
        LinearLayout panel = dialogPanel("API 来源");
        final AlertDialog[] dialogRef = new AlertDialog[1];

        ScrollView sourceScroll = new ScrollView(this);
        LinearLayout sourceList = new LinearLayout(this);
        sourceList.setOrientation(LinearLayout.VERTICAL);
        sourceList.setLayoutTransition(new LayoutTransition());
        sourceScroll.addView(sourceList);

        for (ProviderConfig config : settings.providerConfigs) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, 0, 0, dp(8));

            boolean selected = config.providerId.equals(selectedProviderId);
            Button open = menuRowButton(sourceName(config) + "\n" + config.baseUrl, selected ? "●" : "◦", selected ? "已选" : "›", selected);
            row.addView(open, new LinearLayout.LayoutParams(0, dp(48), 1));

            Button delete = iconButton("×");
            LinearLayout.LayoutParams deleteLp = new LinearLayout.LayoutParams(dp(42), dp(42));
            deleteLp.leftMargin = dp(8);
            row.addView(delete, deleteLp);
            sourceList.addView(row);

            open.setOnClickListener(v -> {
                callback.pick(config);
                if (dialogRef[0] != null) dialogRef[0].dismiss();
            });

            open.setOnLongClickListener(v -> {
                showRenameSourceDialog(config, name -> {
                    open.setText(menuRowText(sourceName(config) + "\n" + config.baseUrl, selected ? "●" : "◦", selected ? "已选" : "›"));
                    settings.save(prefs);
                });
                return true;
            });

            delete.setOnClickListener(v -> {
                if (settings.providerConfigs.size() <= 1) {
                    toast("至少保留一个来源");
                    return;
                }
                boolean wasSelected = config.providerId.equals(selectedProviderId);
                settings.removeConfig(config.providerId);
                settings.save(prefs);
                row.animate()
                        .alpha(0f)
                        .translationX(dp(16))
                        .setDuration(150)
                        .withEndAction(() -> sourceList.removeView(row))
                        .start();
                if (wasSelected) {
                    ProviderConfig fallback = settings.providerConfigs.isEmpty()
                            ? settings.activeConfig()
                            : settings.providerConfigs.get(0);
                    callback.pick(fallback);
                    if (dialogRef[0] != null) dialogRef[0].dismiss();
                }
            });
        }

        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                settings.providerConfigs.size() > 5 ? dp(360) : LinearLayout.LayoutParams.WRAP_CONTENT
        );
        panel.addView(sourceScroll, scrollLp);

        LinearLayout actions = actionRow();
        Button add = filledButton("添加来源", sage, Color.rgb(54, 72, 63));
        Button close = outlineActionButton("关闭");
        LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(0, dp(44), 1);
        addLp.rightMargin = dp(10);
        actions.addView(add, addLp);
        actions.addView(close, new LinearLayout.LayoutParams(0, dp(44), 1));
        panel.addView(actions);

        AlertDialog dialog = showCustomDialog(panel);
        dialogRef[0] = dialog;
        add.setOnClickListener(v -> showAddSourceDialog(config -> {
            callback.pick(config);
            if (dialogRef[0] != null) dialogRef[0].dismiss();
        }));
        close.setOnClickListener(v -> dialog.dismiss());
    }

    private void showAddSourceDialog(SourcePicked callback) {
        LinearLayout panel = dialogPanel("添加来源");
        final AlertDialog[] dialogRef = new AlertDialog[1];

        for (ProviderPreset provider : PROVIDERS) {
            Button row = menuRowButton(provider.name + "\n" + provider.baseUrl, "+", "›", false);
            row.setTextSize(12.5f);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
            lp.bottomMargin = dp(8);
            panel.addView(row, lp);
            row.setOnClickListener(v -> {
                ProviderConfig config = settings.addConfigFromPreset(provider.id);
                settings.save(prefs);
                callback.pick(config);
                if (dialogRef[0] != null) dialogRef[0].dismiss();
            });
        }

        LinearLayout actions = actionRow();
        Button cancel = outlineActionButton("取消");
        actions.addView(cancel, new LinearLayout.LayoutParams(dp(104), dp(44)));
        panel.addView(actions);
        AlertDialog dialog = showCustomDialog(panel);
        dialogRef[0] = dialog;
        cancel.setOnClickListener(v -> dialog.dismiss());
    }

    private void showRenameSourceDialog(ProviderConfig config, RenameDone done) {
        LinearLayout panel = dialogPanel("重命名");
        EditText nameInput = field("名称", sourceName(config), false);
        formAdd(panel, label("来源名称"));
        formAdd(panel, nameInput);
        TextView hint = smallHint("长按 API 来源列表里的条目可以随时改名。");
        panel.addView(hint);

        LinearLayout actions = actionRow();
        Button cancel = outlineActionButton("取消");
        Button save = filledButton("保存", accentDark, Color.WHITE);
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(0, dp(44), 1);
        cancelLp.rightMargin = dp(10);
        actions.addView(cancel, cancelLp);
        actions.addView(save, new LinearLayout.LayoutParams(0, dp(44), 1));
        panel.addView(actions);

        AlertDialog dialog = showCustomDialog(panel);
        cancel.setOnClickListener(v -> dialog.dismiss());
        save.setOnClickListener(v -> {
            String name = nameInput.getText().toString().trim();
            if (!name.isEmpty()) {
                config.name = name;
                done.renamed(name);
                toast("已改名");
            }
            dialog.dismiss();
        });
    }

    private void showRenameConversationDialog(Conversation conversation, Runnable done) {
        LinearLayout panel = dialogPanel("重命名对话");
        EditText nameInput = field("对话名", safe(conversation.title, "新对话"), false);
        formAdd(panel, label("对话名称"));
        formAdd(panel, nameInput);
        TextView hint = smallHint("长按历史对话名即可改名，副本对话也可以单独命名。");
        panel.addView(hint);

        LinearLayout actions = actionRow();
        Button cancel = outlineActionButton("取消");
        Button save = filledButton("保存", accentDark, Color.WHITE);
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(0, dp(44), 1);
        cancelLp.rightMargin = dp(10);
        actions.addView(cancel, cancelLp);
        actions.addView(save, new LinearLayout.LayoutParams(0, dp(44), 1));
        panel.addView(actions);

        AlertDialog dialog = showCustomDialog(panel);
        cancel.setOnClickListener(v -> dialog.dismiss());
        save.setOnClickListener(v -> {
            String name = nameInput.getText().toString().trim();
            if (!name.isEmpty()) {
                conversation.title = name;
                conversation.updatedAt = System.currentTimeMillis();
                done.run();
                toast("已改名");
            }
            dialog.dismiss();
        });
    }

    private void formAdd(LinearLayout panel, View view) {
        panel.addView(view);
    }

    private void showProviderDialog(ProviderPicked callback) {
        LinearLayout panel = dialogPanel("选择服务商");
        final AlertDialog[] dialogRef = new AlertDialog[1];

        for (ProviderPreset provider : PROVIDERS) {
            boolean selected = provider.id.equals(settings.providerId);
            Button row = menuRowButton(provider.name + "\n" + provider.baseUrl, selected ? "●" : "◦", selected ? "已选" : "›", selected);
            row.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
            row.setTextSize(12.5f);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
            lp.bottomMargin = dp(8);
            panel.addView(row, lp);
            row.setOnClickListener(v -> {
                callback.pick(provider);
                if (dialogRef[0] != null) dialogRef[0].dismiss();
            });
        }

        LinearLayout actions = actionRow();
        Button cancel = outlineActionButton("取消");
        actions.addView(cancel, new LinearLayout.LayoutParams(dp(104), dp(44)));
        panel.addView(actions);
        AlertDialog dialog = showCustomDialog(panel);
        dialogRef[0] = dialog;
        cancel.setOnClickListener(v -> dialog.dismiss());
    }

    private List<String> availableModels(String providerId, String modelOptions, String currentModel) {
        Set<String> result = new LinkedHashSet<>();
        ProviderPreset provider = providerById(providerId);
        if (currentModel != null && !currentModel.trim().isEmpty() && !isImageGenerationModel(currentModel)) {
            result.add(currentModel.trim());
        }
        if (modelOptions != null) {
            for (String item : modelOptions.split("[\\n,]")) {
                String model = item.trim();
                if (!model.isEmpty() && !isImageGenerationModel(model)) result.add(model);
            }
        }
        for (String model : provider.models) {
            if (!isImageGenerationModel(model)) result.add(model);
        }
        return new ArrayList<>(result);
    }

    private List<String> availableImageModels(String providerId, String modelOptions, String currentModel) {
        Set<String> result = new LinkedHashSet<>();
        ProviderPreset provider = providerById(providerId);
        if (currentModel != null && !currentModel.trim().isEmpty()) result.add(currentModel.trim());
        if (modelOptions != null) {
            for (String item : modelOptions.split("[\\n,]")) {
                String model = item.trim();
                if (!model.isEmpty() && isImageGenerationModel(model)) result.add(model);
            }
        }
        for (String model : provider.imageModels) result.add(model);
        if (result.isEmpty()) result.add("gpt-image-1");
        return new ArrayList<>(result);
    }

    private String defaultModelFor(ProviderConfig config) {
        if (config == null) return "deepseek-v4-flash";
        String saved = safe(config.model, "").trim();
        List<String> models = availableModels(config.providerId, config.modelOptions, null);
        if (!saved.isEmpty() && models.contains(saved)) return saved;
        return models.isEmpty() ? safe(saved, "deepseek-v4-flash") : models.get(0);
    }

    private String defaultImageModelFor(String providerId, String modelOptions, String currentModel) {
        List<String> models = availableImageModels(providerId, modelOptions, currentModel);
        return models.isEmpty() ? "gpt-image-1" : models.get(0);
    }

    private ProviderPreset providerById(String id) {
        ProviderConfig existing = settings.findConfig(id);
        if (existing != null && existing.presetId != null && !existing.presetId.isEmpty()) {
            id = existing.presetId;
        }
        if (id != null && id.contains(":")) {
            id = id.substring(0, id.indexOf(':'));
        }
        for (ProviderPreset provider : PROVIDERS) {
            if (provider.id.equals(id)) return provider;
        }
        if (id != null && id.startsWith("proxy:")) {
            ProviderConfig config = settings.configFor(id);
            return new ProviderPreset(id, "中转 " + config.baseUrl, config.baseUrl,
                    new String[]{"gpt-4o-mini", "deepseek-chat", "claude-3.5-sonnet", "gemini-2.5-flash"},
                    new String[]{"gpt-image-1", "gpt-image-1-mini", "dall-e-3"});
        }
        return PROVIDERS[0];
    }

    private String providerName(String id) {
        return providerById(id).name;
    }

    private String sourceName(ProviderConfig config) {
        if (config == null) return "API 来源";
        if (config.name != null && !config.name.trim().isEmpty()) return config.name;
        return providerById(config.providerId).name;
    }

    private List<String> fetchModelList(String providerId, String baseUrl, String apiKey) throws Exception {
        URL url = new URL(trimSlash(baseUrl) + "/models");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(30000);
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            if (isAnthropicProvider(providerId, baseUrl)) {
                conn.setRequestProperty("x-api-key", apiKey.trim());
                conn.setRequestProperty("anthropic-version", "2023-06-01");
            } else {
                conn.setRequestProperty("Authorization", "Bearer " + apiKey.trim());
            }
        }

        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) {
            String error = readAll(conn.getErrorStream());
            throw new RuntimeException(error.isEmpty() ? ("HTTP " + code) : error);
        }

        JSONObject json = new JSONObject(readAll(conn.getInputStream()));
        JSONArray data = json.optJSONArray("data");
        List<String> models = new ArrayList<>();
        if (data != null) {
            for (int i = 0; i < data.length(); i++) {
                Object item = data.opt(i);
                String id = "";
                if (item instanceof JSONObject) id = ((JSONObject) item).optString("id", "");
                else if (item != null) id = String.valueOf(item);
                if (!id.trim().isEmpty()) models.add(id.trim());
            }
        }
        return models;
    }

    private boolean isAnthropicProvider(RequestConfig requestConfig) {
        return isAnthropicProvider(requestConfig.providerId, requestConfig.baseUrl);
    }

    private boolean isAnthropicProvider(String providerId, String baseUrl) {
        String id = safe(providerId, "");
        ProviderConfig existing = settings.findConfig(id);
        if (existing != null && existing.presetId != null && !existing.presetId.isEmpty()) {
            id = existing.presetId;
        }
        if (id.contains(":")) id = id.substring(0, id.indexOf(':'));
        String cleanUrl = safe(baseUrl, "").toLowerCase();
        return "anthropic".equals(id) || "claude".equals(id) || cleanUrl.contains("api.anthropic.com");
    }

    private String joinModels(List<String> models) {
        StringBuilder sb = new StringBuilder();
        for (String model : models) {
            if (model == null || model.trim().isEmpty()) continue;
            if (sb.length() > 0) sb.append('\n');
            sb.append(model.trim());
        }
        return sb.toString();
    }

    private void saveProviderDraft(String providerId, EditText baseUrl, EditText apiKey, String model, String modelOptions) {
        ProviderConfig config = settings.configFor(providerId);
        config.baseUrl = baseUrl.getText().toString().trim();
        config.apiKey = apiKey.getText().toString().trim();
        config.model = model;
        config.modelOptions = modelOptions;
    }

    private void saveImageProviderDraft(String providerId, EditText baseUrl, EditText apiKey, String modelOptions) {
        ProviderConfig config = settings.configFor(providerId);
        config.baseUrl = baseUrl.getText().toString().trim();
        config.apiKey = apiKey.getText().toString().trim();
        config.modelOptions = modelOptions;
        settings.imageBaseUrl = config.baseUrl;
        settings.imageApiKey = config.apiKey;
        settings.imageModelOptions = modelOptions;
    }

    private EditText field(String hint, String value, boolean password) {
        EditText edit = new EditText(this);
        edit.setHint(hint);
        edit.setText(value);
        edit.setSingleLine(!hint.equals("System Prompt"));
        edit.setTextColor(text);
        edit.setHintTextColor(muted);
        edit.setTextSize(13);
        edit.setBackground(makeStrokeBg(Color.rgb(250, 246, 239), line, dp(12)));
        edit.setPadding(dp(12), 0, dp(12), 0);
        if (password) edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        return edit;
    }

    private LinearLayout dialogPanel(String title) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(18), dp(18), dp(16));
        panel.setBackground(makeSolidBg(page, dp(16)));

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(accentDark);
        titleView.setTextSize(22);
        titleView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        titleView.setPadding(0, 0, 0, dp(14));
        panel.addView(titleView);

        return panel;
    }

    private AlertDialog showCustomDialog(View view) {
        AlertDialog dialog = new AlertDialog.Builder(this).create();
        int maxHeight = Math.max(dp(360), getResources().getDisplayMetrics().heightPixels - dp(64));
        ScrollView sheetScroll = new BoundedScrollView(this, maxHeight, dp(18));
        sheetScroll.setBackground(makeSolidBg(page, dp(18)));
        sheetScroll.setClipToOutline(true);
        sheetScroll.setFillViewport(false);
        sheetScroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        sheetScroll.addView(view);
        dialog.setView(sheetScroll, 0, 0, 0, 0);
        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                dialog.getWindow().setGravity(Gravity.CENTER);
                dialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                android.view.WindowManager.LayoutParams attrs = dialog.getWindow().getAttributes();
                attrs.dimAmount = 0.58f;
                dialog.getWindow().setAttributes(attrs);
                int width = Math.min(
                        getResources().getDisplayMetrics().widthPixels - dp(32),
                        dp(420)
                );
                dialog.getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        });
        dialog.show();
        return dialog;
    }

    private LinearLayout actionRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(16), 0, 0);
        return row;
    }

    private Button textActionButton(String value) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(value);
        button.setTextSize(15);
        button.setTextColor(accent);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setPadding(dp(16), 0, dp(16), 0);
        button.setStateListAnimator(null);
        button.setBackground(makeSolidBg(Color.TRANSPARENT, dp(12)));
        addPressAnimation(button);
        return button;
    }

    private Button tinyActionButton(String value) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(value);
        button.setTextSize(11.5f);
        button.setTextColor(accentSoft);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setPadding(dp(10), 0, dp(10), 0);
        button.setStateListAnimator(null);
        button.setBackground(makeStrokeBg(Color.rgb(255, 252, 246), Color.rgb(229, 221, 210), dp(999)));
        addPressAnimation(button);
        return button;
    }

    private Button flatButton(String value) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(value);
        button.setTextColor(text);
        button.setTextSize(14);
        button.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setPadding(dp(12), 0, dp(12), 0);
        button.setStateListAnimator(null);
        addPressAnimation(button);
        return button;
    }

    private Button filledButton(String value, int fill, int color) {
        Button button = flatButton(value);
        button.setTextColor(color);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setGravity(Gravity.CENTER);
        button.setBackground(makeSolidBg(fill, dp(14)));
        return button;
    }

    private Button outlineActionButton(String value) {
        Button button = flatButton(value);
        button.setTextColor(accentDark);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setGravity(Gravity.CENTER);
        button.setBackground(makeStrokeBg(page, line, dp(14)));
        return button;
    }

    private Button menuRowButton(String value, String leading, String trailing, boolean selected) {
        Button button = flatButton(menuRowText(value, leading, trailing));
        button.setTextColor(selected ? accentDark : text);
        button.setTextSize(14);
        button.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        button.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        button.setPadding(dp(12), 0, dp(12), 0);
        button.setBackground(menuRowBg(selected));
        return button;
    }

    private String menuRowText(String value, String leading, String trailing) {
        String prefix = leading == null || leading.isEmpty() ? "" : leading + "  ";
        String suffix = trailing == null || trailing.isEmpty() ? "" : "  " + trailing;
        return prefix + value + suffix;
    }

    private android.graphics.drawable.Drawable menuRowBg(boolean selected) {
        return makeStrokeBg(
                selected ? Color.rgb(248, 239, 226) : Color.rgb(250, 246, 239),
                selected ? Color.rgb(226, 209, 190) : line,
                dp(14)
        );
    }

    private Button modelRow(String model, boolean selected) {
        Button row = flatButton((selected ? "●  " : "○  ") + model + "\n" + modelDescription(model));
        row.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        row.setTextColor(selected ? accentDark : text);
        row.setTextSize(14);
        row.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        row.setBackground(menuRowBg(selected));
        return row;
    }

    private String modelDescription(String model) {
        if ("deepseek-v4-flash".equals(model)) return "快速、日常聊天";
        if ("deepseek-v4-pro".equals(model)) return "更强、复杂任务";
        if ("deepseek-chat".equals(model)) return "通用兼容模型";
        if ("deepseek-reasoner".equals(model)) return "推理和分析";
        if (model.startsWith("claude-opus")) return "Claude 旗舰模型";
        if (model.startsWith("claude-sonnet")) return "Claude 平衡模型";
        if (model.startsWith("claude-") && model.contains("haiku")) return "Claude 快速模型";
        if (isImageGenerationModel(model)) return "文生图模型";
        return "自定义模型";
    }

    private interface ModelPicked {
        void pick(String model);
    }

    private interface ProviderPicked {
        void pick(ProviderPreset provider);
    }

    private interface SourcePicked {
        void pick(ProviderConfig config);
    }

    private interface RenameDone {
        void renamed(String name);
    }

    private static class BoundedScrollView extends ScrollView {
        private final int maxHeight;
        private final int radius;

        BoundedScrollView(Context context, int maxHeight, int radius) {
            super(context);
            this.maxHeight = maxHeight;
            this.radius = radius;
            setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override
                public void getOutline(View view, android.graphics.Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), BoundedScrollView.this.radius);
                }
            });
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int cappedHeight = View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST);
            super.onMeasure(widthMeasureSpec, cappedHeight);
        }
    }

    private static class BoundedLinearLayout extends LinearLayout {
        private final int maxWidth;

        BoundedLinearLayout(Context context, int maxWidth) {
            super(context);
            this.maxWidth = maxWidth;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int cappedWidth = View.MeasureSpec.makeMeasureSpec(maxWidth, View.MeasureSpec.AT_MOST);
            super.onMeasure(cappedWidth, heightMeasureSpec);
        }
    }

    static class ProviderPreset {
        final String id;
        final String name;
        final String baseUrl;
        final String[] models;
        final String[] imageModels;

        ProviderPreset(String id, String name, String baseUrl, String[] models) {
            this(id, name, baseUrl, models, new String[0]);
        }

        ProviderPreset(String id, String name, String baseUrl, String[] models, String[] imageModels) {
            this.id = id;
            this.name = name;
            this.baseUrl = baseUrl;
            this.models = models;
            this.imageModels = imageModels;
        }
    }

    static class RequestConfig {
        String providerId;
        String baseUrl;
        String apiKey;
        String model;
        boolean thinkingEnabled;
        double temperature;
        int maxTokens;
        String imageProviderId;
        String imageBaseUrl;
        String imageApiKey;
        String imageModel;
        String imageModelOptions;
        String imageSize;
        String systemPrompt;

        static RequestConfig from(Settings settings, Conversation conversation) {
            RequestConfig config = new RequestConfig();
            config.providerId = settings.providerId;
            config.baseUrl = settings.baseUrl;
            config.apiKey = settings.apiKey;
            config.model = settings.model;
            config.thinkingEnabled = settings.thinkingEnabled;
            config.temperature = settings.temperature;
            config.maxTokens = settings.maxTokens;
            config.imageProviderId = settings.imageProviderId;
            config.imageBaseUrl = settings.imageBaseUrl;
            config.imageApiKey = settings.imageApiKey;
            config.imageModel = settings.imageModel;
            config.imageModelOptions = settings.imageModelOptions;
            config.imageSize = settings.imageSize;
            config.systemPrompt = conversation != null && conversation.systemPrompt != null
                    ? conversation.systemPrompt
                    : Settings.DEFAULT_SYSTEM_PROMPT;
            return config;
        }
    }

    private void createConversation(boolean render) {
        Conversation c = new Conversation();
        c.id = createId();
        c.title = "新对话";
        c.createdAt = System.currentTimeMillis();
        c.updatedAt = c.createdAt;
        c.systemPrompt = Settings.DEFAULT_SYSTEM_PROMPT;
        conversations.add(0, c);
        activeConversationId = c.id;
        saveConversations();
        if (render) {
            renderMessages();
            refreshBusyState();
        }
    }

    private Conversation activeConversation() {
        for (Conversation c : conversations) {
            if (c.id.equals(activeConversationId)) return c;
        }
        createConversation(false);
        return conversations.get(0);
    }

    private Conversation findConversation(String id) {
        for (Conversation c : conversations) {
            if (c.id.equals(id)) return c;
        }
        return null;
    }

    private String rootConversationId(Conversation conversation) {
        if (conversation == null) return "";
        return conversation.parentId == null || conversation.parentId.trim().isEmpty()
                ? conversation.id
                : conversation.parentId;
    }

    private boolean isBranchConversation(Conversation conversation) {
        return conversation != null && conversation.parentId != null && !conversation.parentId.trim().isEmpty();
    }

    private int indexOfMessage(Conversation conversation, String messageId) {
        if (conversation == null || messageId == null) return -1;
        for (int i = 0; i < conversation.messages.size(); i++) {
            if (messageId.equals(conversation.messages.get(i).id)) return i;
        }
        return -1;
    }

    private int previousUserIndex(Conversation conversation, int fromIndex) {
        if (conversation == null) return -1;
        for (int i = Math.min(fromIndex - 1, conversation.messages.size() - 1); i >= 0; i--) {
            if ("user".equals(conversation.messages.get(i).role)) return i;
        }
        return -1;
    }

    private int lastUserIndex(Conversation conversation) {
        if (conversation == null) return -1;
        for (int i = conversation.messages.size() - 1; i >= 0; i--) {
            if ("user".equals(conversation.messages.get(i).role)) return i;
        }
        return -1;
    }

    private void removeMessagesAfter(Conversation conversation, int index) {
        while (conversation.messages.size() > index + 1) {
            conversation.messages.remove(conversation.messages.size() - 1);
        }
    }

    private Conversation createBranchCopy(Conversation source, int throughIndex, String reason) {
        Conversation branch = new Conversation();
        branch.id = createId();
        branch.parentId = rootConversationId(source);
        branch.title = branchTitle(source, reason);
        branch.systemPrompt = conversationSystemPrompt(source);
        branch.createdAt = System.currentTimeMillis();
        branch.updatedAt = branch.createdAt;
        int end = Math.min(throughIndex, source.messages.size() - 1);
        for (int i = 0; i <= end; i++) {
            branch.messages.add(source.messages.get(i).copy());
        }

        Conversation root = findConversation(branch.parentId);
        if (root != null) root.branchesOpen = true;
        int insertAt = 0;
        for (int i = 0; i < conversations.size(); i++) {
            if (conversations.get(i).id.equals(branch.parentId)) {
                insertAt = i + 1;
                break;
            }
        }
        conversations.add(insertAt, branch);
        return branch;
    }

    private String branchTitle(Conversation source, String reason) {
        String base = safe(source.title, "新对话");
        String suffix = safe(reason, "副本");
        String title = base + " · " + suffix;
        return title.length() > 26 ? title.substring(0, 26) + "..." : title;
    }

    private void moveConversationToTop(String id) {
        for (int i = 0; i < conversations.size(); i++) {
            if (conversations.get(i).id.equals(id)) {
                if (i == 0) return;
                Conversation c = conversations.remove(i);
                conversations.add(0, c);
                return;
            }
        }
    }

    private void loadConversations() {
        conversations.clear();
        activeConversationId = prefs.getString("activeConversationId", null);
        String legacySystemPrompt = prefs.getString("systemPrompt", Settings.DEFAULT_SYSTEM_PROMPT);
        boolean needsSystemPromptMigration = !prefs.getBoolean("conversationSystemPromptDefaultMigrated", false);
        boolean changed = false;
        try {
            JSONArray arr = new JSONArray(prefs.getString("conversations", "[]"));
            for (int i = 0; i < arr.length(); i++) conversations.add(Conversation.fromJson(arr.getJSONObject(i)));
        } catch (Exception ignored) {
        }
        for (Conversation conversation : conversations) {
            if (conversation.systemPrompt == null
                    || (needsSystemPromptMigration && conversation.systemPrompt.equals(legacySystemPrompt))) {
                conversation.systemPrompt = Settings.DEFAULT_SYSTEM_PROMPT;
                changed = true;
            }
        }
        if (needsSystemPromptMigration) {
            prefs.edit()
                    .putString("systemPrompt", Settings.DEFAULT_SYSTEM_PROMPT)
                    .putBoolean("conversationSystemPromptDefaultMigrated", true)
                    .apply();
            settings.systemPrompt = Settings.DEFAULT_SYSTEM_PROMPT;
            changed = true;
        }
        if (changed) saveConversations();
    }

    private void saveConversations() {
        JSONArray arr = new JSONArray();
        try {
            for (Conversation c : conversations) arr.put(c.toJson());
        } catch (Exception ignored) {
        }
        prefs.edit()
                .putString("conversations", arr.toString())
                .putString("activeConversationId", activeConversationId)
                .apply();
    }

    private Button iconButton(String value) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(value);
        b.setTextColor(muted);
        b.setTextSize(18);
        b.setMinWidth(0);
        b.setMinHeight(0);
        b.setPadding(0, 0, 0, 0);
        b.setStateListAnimator(null);
        b.setBackground(makeStrokeBg(Color.rgb(255, 255, 255), line, dp(15)));
        addPressAnimation(b);
        return b;
    }

    private android.graphics.drawable.Drawable makeStrokeBg(int fill, int stroke, int radius) {
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(fill);
        bg.setCornerRadius(radius);
        bg.setStroke(1, stroke);
        return bg;
    }

    private android.graphics.drawable.Drawable makeSolidBg(int fill, int radius) {
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(fill);
        bg.setCornerRadius(radius);
        return bg;
    }

    private void startGeneration(String conversationId) {
        generatingConversationIds.add(conversationId);
        refreshBusyState();
    }

    private void finishGeneration(String conversationId) {
        generatingConversationIds.remove(conversationId);
        refreshBusyState();
    }

    private boolean isConversationGenerating(String conversationId) {
        return conversationId != null && generatingConversationIds.contains(conversationId);
    }

    private boolean isActiveConversationGenerating() {
        return isConversationGenerating(activeConversationId);
    }

    private void refreshBusyState() {
        activeGeneration = isActiveConversationGenerating();
        setBusy(activeGeneration);
    }

    private void setBusy(boolean busy) {
        if (sendButton == null || modelButton == null || thinkingButton == null || promptInput == null) return;
        sendButton.setEnabled(!busy);
        modelButton.setEnabled(!busy);
        thinkingButton.setEnabled(!busy);
        promptInput.setEnabled(!busy);
        if (imageModeButton != null) imageModeButton.setEnabled(!busy);
        sendButton.animate()
                .alpha(busy ? 0.55f : 1.0f)
                .scaleX(busy ? 0.96f : 1.0f)
                .scaleY(busy ? 0.96f : 1.0f)
                .setDuration(140)
                .start();
    }

    private void addPressAnimation(View view) {
        view.setOnTouchListener((target, event) -> {
            if (!target.isEnabled()) return false;
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                target.animate().scaleX(0.96f).scaleY(0.96f).setDuration(80).start();
            } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                target.animate().scaleX(1f).scaleY(1f).setDuration(110).start();
            }
            return false;
        });
    }

    private void animateIn(View view) {
        view.setAlpha(0f);
        view.setTranslationY(dp(8));
        view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(180)
                .start();
    }

    private void startSpinner(View view) {
        ObjectAnimator animator = ObjectAnimator.ofFloat(view, "rotation", 0f, 360f);
        animator.setDuration(900);
        animator.setRepeatCount(ObjectAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.start();
    }

    private void updateModelButton() {
        if (modelButton != null) modelButton.setText(modelLabel(settings.model));
        if (thinkingButton != null) thinkingButton.setText(thinkingLabel());
    }

    private void toggleThinking() {
        settings.thinkingEnabled = !settings.thinkingEnabled;
        settings.save(prefs);
        updateModelButton();
        toast(settings.thinkingEnabled ? "\u5df2\u5f00\u542f\u601d\u8003" : "\u5df2\u5173\u95ed\u601d\u8003");
    }

    private void toggleImageMode() {
        imageModeEnabled = !imageModeEnabled;
        updateImageModeButton();
        toast(imageModeEnabled ? "文生图模式" : "聊天模式");
    }

    private void updateImageModeButton() {
        if (imageModeButton == null || promptInput == null) return;
        imageModeButton.setTextColor(imageModeEnabled ? Color.WHITE : muted);
        imageModeButton.setBackground(imageModeEnabled
                ? makeSolidBg(accent, dp(22))
                : makeStrokeBg(Color.rgb(255, 255, 255), line, dp(15)));
        promptInput.setHint(imageModeEnabled ? "描述想生成的图片..." : "输入消息...");
    }

    private String thinkingLabel() {
        return settings.thinkingEnabled ? "\u601d\u8003" : "\u5feb\u7b54";
    }

    private String modelLabel(String model) {
        if ("deepseek-v4-flash".equals(model)) return "v4-flash";
        if ("deepseek-v4-pro".equals(model)) return "v4-pro";
        if ("deepseek-chat".equals(model)) return "chat";
        if ("deepseek-reasoner".equals(model)) return "reasoner";
        if ("gpt-image-1".equals(model)) return "image-1";
        if ("gpt-image-1-mini".equals(model)) return "image-mini";
        if (model.startsWith("claude-opus")) return "opus";
        if (model.startsWith("claude-sonnet") || model.contains("sonnet")) return "sonnet";
        if (model.startsWith("claude-") && model.contains("haiku")) return "haiku";
        return safe(model, "模型");
    }

    private boolean isImageGenerationModel(String model) {
        String clean = safe(model, "").toLowerCase();
        return clean.startsWith("gpt-image")
                || clean.startsWith("dall-e")
                || clean.contains("image-generation")
                || clean.contains("stable-image");
    }

    private boolean isGptImageModel(String model) {
        return safe(model, "").toLowerCase().startsWith("gpt-image");
    }

    private String imageGenerationModel() {
        return safe(settings.imageModel, "gpt-image-1");
    }

    private String imageGenerationModel(RequestConfig requestConfig) {
        return safe(requestConfig.imageModel, "gpt-image-1");
    }

    private boolean shouldUseImageEndpoint(RequestConfig requestConfig) {
        return imageModeEnabled;
    }

    private boolean supportsThinkingParameter(RequestConfig requestConfig) {
        String provider = safe(requestConfig.providerId, "").toLowerCase();
        String model = safe(requestConfig.model, "").toLowerCase();
        return provider.contains("deepseek")
                || model.contains("deepseek")
                || model.contains("reasoner");
    }

    private void scrollToBottom() {
        boolean generationSensitive = activeGeneration;
        mainHandler.postDelayed(() -> {
            if (generationSensitive && !autoFollowBottom) return;
            scrollView.fullScroll(View.FOCUS_DOWN);
        }, 50);
    }

    private boolean isAtBottom() {
        if (scrollView == null || scrollView.getChildCount() == 0) return true;
        View child = scrollView.getChildAt(0);
        int distance = child.getBottom() - (scrollView.getScrollY() + scrollView.getHeight());
        return distance <= dp(24);
    }

    private void adjustPromptHeight() {
        promptInput.post(() -> {
            int minHeight = dp(46);
            int maxHeight = dp(184);
            int wanted = promptInput.getLineCount() * promptInput.getLineHeight()
                    + promptInput.getCompoundPaddingTop()
                    + promptInput.getCompoundPaddingBottom()
                    + dp(2);
            int height = Math.max(minHeight, Math.min(maxHeight, wanted));
            ViewGroup.LayoutParams params = promptInput.getLayoutParams();
            if (params != null && params.height != height) {
                params.height = height;
                promptInput.setLayoutParams(params);
            }
        });
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(promptInput.getWindowToken(), 0);
    }

    private void toast(String value) {
        Toast.makeText(this, value, Toast.LENGTH_SHORT).show();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private String makeTitle(String value) {
        String clean = safe(value, "新对话").replaceAll("\\s+", " ").trim();
        return clean.length() > 18 ? clean.substring(0, 18) + "..." : clean;
    }

    private String trimSlash(String value) {
        if (value == null || value.trim().isEmpty()) return "https://api.deepseek.com/v1";
        return value.replaceAll("/+$", "");
    }

    private String readAll(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    private String readableError(Exception error) {
        if (error instanceof java.net.SocketTimeoutException) {
            return "连接超时。请检查网络、代理或中转站是否可用。";
        }
        if (error instanceof java.net.UnknownHostException) {
            return "无法解析服务器地址。请检查 Base URL 是否正确，或当前网络是否可访问。";
        }
        if (error instanceof java.net.ConnectException) {
            return "无法连接到服务器。请检查 Base URL、中转站状态或网络代理。";
        }

        String message = safe(error.getMessage(), error.getClass().getSimpleName());
        String extracted = extractApiError(message);
        if (!extracted.isEmpty()) message = extracted;
        String lower = message.toLowerCase();
        if (lower.contains("401") || lower.contains("unauthorized") || lower.contains("invalid api key") || lower.contains("invalid token")) {
            return "API Key 无效或没有权限。请检查 Key 是否填错，或当前来源是否选对。";
        }
        if (lower.contains("403") || lower.contains("forbidden")) {
            return "请求被拒绝。这个 Key 可能没有模型权限，或中转站限制了访问。";
        }
        if (lower.contains("404") || lower.contains("model")) {
            return "模型或接口不存在。请点“获取模型”重新选择，或确认 Base URL 末尾是 /v1。";
        }
        if (lower.contains("429") || lower.contains("rate limit") || lower.contains("quota")) {
            return "额度不足或请求过快。请稍后再试，或检查账户余额/限速。";
        }
        if (lower.contains("timeout")) {
            return "请求等待太久。请检查网络或换一个更稳定的来源。";
        }
        if (message.length() > 240) message = message.substring(0, 240) + "...";
        return message;
    }

    private String extractApiError(String message) {
        try {
            JSONObject json = new JSONObject(message);
            Object error = json.opt("error");
            if (error instanceof JSONObject) {
                String value = ((JSONObject) error).optString("message", "");
                if (!value.isEmpty()) return value;
            }
            if (error instanceof String) return String.valueOf(error);
            return json.optString("message", "");
        } catch (Exception ignored) {
            return "";
        }
    }

    private String tokenFrom(JSONObject obj, String key) {
        Object value = obj.opt(key);
        if (value == null || value == JSONObject.NULL) return "";
        String token = String.valueOf(value);
        return "null".equals(token) ? "" : token;
    }

    private String safe(String value, String fallback) {
        return value == null || value.isEmpty() ? fallback : value;
    }

    private void enableCopy(TextView view) {
        view.setOnLongClickListener(v -> {
            String value = ((TextView) v).getText().toString();
            if (value.trim().isEmpty()) return true;
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("message", value));
                toast("\u5df2\u590d\u5236");
            }
            return true;
        });
    }

    private double parseDouble(String value, double fallback) {
        try {
            return Double.parseDouble(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String createId() {
        return "id-" + System.currentTimeMillis() + "-" + Math.round(Math.random() * 1000000);
    }

    static class Settings {
        static final String DEFAULT_SYSTEM_PROMPT = "你是一个有帮助、表达清晰的助手。";
        String providerId = "deepseek";
        String baseUrl = "https://api.deepseek.com/v1";
        String apiKey = "";
        String model = "deepseek-v4-flash";
        String modelOptions = "";
        List<ProviderConfig> providerConfigs = new ArrayList<>();
        boolean thinkingEnabled = true;
        double temperature = 0.7;
        int maxTokens = 2048;
        String imageProviderId = "openai";
        String imageBaseUrl = "https://api.openai.com/v1";
        String imageApiKey = "";
        String imageModel = "gpt-image-1";
        String imageModelOptions = "";
        String imageSize = "1024x1024";
        String systemPrompt = DEFAULT_SYSTEM_PROMPT;

        static Settings load(SharedPreferences prefs) {
            Settings s = new Settings();
            s.providerId = prefs.getString("providerId", s.providerId);
            s.baseUrl = prefs.getString("baseUrl", s.baseUrl);
            s.apiKey = prefs.getString("apiKey", s.apiKey);
            s.model = prefs.getString("model", s.model);
            s.modelOptions = prefs.getString("modelOptions", s.modelOptions);
            s.loadProviderConfigs(prefs);
            s.thinkingEnabled = prefs.getBoolean("thinkingEnabled", s.thinkingEnabled);
            s.temperature = Double.longBitsToDouble(prefs.getLong("temperature", Double.doubleToLongBits(s.temperature)));
            s.maxTokens = prefs.getInt("maxTokens", s.maxTokens);
            String savedImageProviderId = prefs.getString("imageProviderId", "");
            s.imageProviderId = savedImageProviderId == null || savedImageProviderId.trim().isEmpty()
                    ? s.defaultImageProviderId()
                    : savedImageProviderId;
            s.imageBaseUrl = prefs.getString("imageBaseUrl", "");
            s.imageApiKey = prefs.getString("imageApiKey", "");
            s.imageModel = prefs.getString("imageModel", s.imageModel);
            s.imageModelOptions = prefs.getString("imageModelOptions", "");
            s.imageSize = prefs.getString("imageSize", s.imageSize);
            s.systemPrompt = DEFAULT_SYSTEM_PROMPT;
            s.configFor(s.providerId);
            s.applyActiveProvider();
            s.configFor(s.imageProviderId);
            s.applyImageProvider();
            return s;
        }

        void save(SharedPreferences prefs) {
            prefs.edit()
                    .putString("providerId", providerId)
                    .putString("baseUrl", baseUrl)
                    .putString("apiKey", apiKey)
                    .putString("model", model)
                    .putString("modelOptions", modelOptions)
                    .putString("providerConfigs", providerConfigsJson())
                    .putBoolean("thinkingEnabled", thinkingEnabled)
                    .putLong("temperature", Double.doubleToLongBits(temperature))
                    .putInt("maxTokens", maxTokens)
                    .putString("imageProviderId", imageProviderId)
                    .putString("imageBaseUrl", imageBaseUrl)
                    .putString("imageApiKey", imageApiKey)
                    .putString("imageModel", imageModel)
                    .putString("imageModelOptions", imageModelOptions)
                    .putString("imageSize", imageSize)
                    .putString("systemPrompt", systemPrompt)
                    .apply();
        }

        ProviderConfig configFor(String providerId) {
            ProviderConfig found = findConfig(providerId);
            if (found != null) return found;

            ProviderConfig config = ProviderConfig.fromPreset(providerId);
            if (providerId.equals(this.providerId)) {
                config.baseUrl = baseUrl;
                config.apiKey = apiKey;
                config.model = model;
                config.modelOptions = modelOptions;
            }
            if (providerId.equals(this.imageProviderId)) {
                if (imageBaseUrl != null && !imageBaseUrl.trim().isEmpty()) config.baseUrl = imageBaseUrl;
                if (imageApiKey != null && !imageApiKey.trim().isEmpty()) config.apiKey = imageApiKey;
                if (imageModelOptions != null && !imageModelOptions.trim().isEmpty()) config.modelOptions = imageModelOptions;
            }
            providerConfigs.add(config);
            return config;
        }

        ProviderConfig findConfig(String providerId) {
            for (ProviderConfig config : providerConfigs) {
                if (config.providerId.equals(providerId)) return config;
            }
            return null;
        }

        ProviderConfig activeConfig() {
            if (providerConfigs.isEmpty()) {
                ProviderConfig config = ProviderConfig.fromPreset("deepseek");
                providerConfigs.add(config);
                providerId = config.providerId;
            }
            ProviderConfig config = findConfig(providerId);
            if (config != null) return config;
            providerId = providerConfigs.get(0).providerId;
            return providerConfigs.get(0);
        }

        ProviderConfig addConfigFromPreset(String presetId) {
            ProviderConfig config = ProviderConfig.fromPreset(presetId);
            config.providerId = presetId + ":" + System.currentTimeMillis() + ":" + Math.round(Math.random() * 10000);
            config.name = nextConfigName(presetId);
            providerConfigs.add(config);
            return config;
        }

        void removeConfig(String id) {
            for (int i = providerConfigs.size() - 1; i >= 0; i--) {
                if (providerConfigs.get(i).providerId.equals(id)) providerConfigs.remove(i);
            }
            if (providerConfigs.isEmpty()) providerConfigs.add(ProviderConfig.fromPreset("deepseek"));
            if (providerId.equals(id)) providerId = providerConfigs.get(0).providerId;
            if (imageProviderId.equals(id)) imageProviderId = providerConfigs.get(0).providerId;
            applyActiveProvider();
            applyImageProvider();
        }

        String nextConfigName(String presetId) {
            String baseName = "API 来源";
            for (ProviderPreset preset : PROVIDERS) {
                if (preset.id.equals(presetId)) {
                    baseName = preset.name;
                    break;
                }
            }
            int count = 1;
            for (ProviderConfig config : providerConfigs) {
                if (presetId.equals(config.presetId) || presetId.equals(config.providerId)) count++;
            }
            return count <= 1 ? baseName : baseName + " " + count;
        }

        String defaultImageProviderId() {
            if (providerSupportsImages(providerId, presetIdFor(providerId))) return providerId;
            for (ProviderConfig config : providerConfigs) {
                if (providerSupportsImages(config.providerId, config.presetId)) return config.providerId;
            }
            return "openai";
        }

        String presetIdFor(String id) {
            ProviderConfig config = findConfig(id);
            if (config != null && config.presetId != null && !config.presetId.trim().isEmpty()) {
                return config.presetId;
            }
            if (id != null && id.contains(":")) return id.substring(0, id.indexOf(':'));
            return id;
        }

        static boolean providerSupportsImages(String providerId, String presetId) {
            String id = presetId == null || presetId.trim().isEmpty() ? providerId : presetId;
            if (id != null && id.contains(":")) id = id.substring(0, id.indexOf(':'));
            for (ProviderPreset preset : PROVIDERS) {
                if (preset.id.equals(id)) return preset.imageModels.length > 0;
            }
            return false;
        }

        void applyActiveProvider() {
            ProviderConfig config = configFor(providerId);
            baseUrl = config.baseUrl;
            apiKey = config.apiKey;
            model = config.model;
            modelOptions = config.modelOptions;
        }

        ProviderConfig imageConfig() {
            if (imageProviderId == null || imageProviderId.trim().isEmpty()) imageProviderId = providerId;
            return configFor(imageProviderId);
        }

        void applyImageProvider() {
            ProviderConfig config = imageConfig();
            imageProviderId = config.providerId;
            if (imageBaseUrl == null || imageBaseUrl.trim().isEmpty()) {
                imageBaseUrl = config.baseUrl;
            } else {
                config.baseUrl = imageBaseUrl;
            }
            if (imageApiKey == null || imageApiKey.trim().isEmpty()) {
                imageApiKey = config.apiKey;
            } else {
                config.apiKey = imageApiKey;
            }
            if (imageModelOptions == null || imageModelOptions.trim().isEmpty()) {
                imageModelOptions = config.modelOptions;
            } else {
                config.modelOptions = imageModelOptions;
            }
        }

        void loadProviderConfigs(SharedPreferences prefs) {
            String raw = prefs.getString("providerConfigs", "");
            if (raw == null || raw.trim().isEmpty()) return;
            try {
                JSONArray arr = new JSONArray(raw);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.optJSONObject(i);
                    if (obj != null) providerConfigs.add(ProviderConfig.fromJson(obj));
                }
            } catch (Exception ignored) {}
        }

        String providerConfigsJson() {
            JSONArray arr = new JSONArray();
            try {
                for (ProviderConfig config : providerConfigs) arr.put(config.toJson());
            } catch (Exception ignored) {}
            return arr.toString();
        }
    }

    static class ProviderConfig {
        String providerId;
        String presetId;
        String name;
        String baseUrl;
        String apiKey = "";
        String model;
        String modelOptions = "";

        static ProviderConfig fromPreset(String providerId) {
            ProviderPreset preset = PROVIDERS[0];
            for (ProviderPreset item : PROVIDERS) {
                if (item.id.equals(providerId)) {
                    preset = item;
                    break;
                }
            }
            ProviderConfig config = new ProviderConfig();
            config.providerId = preset.id;
            config.presetId = preset.id;
            config.name = preset.name;
            if (providerId != null && providerId.startsWith("proxy:")) {
                config.providerId = providerId;
                config.presetId = "proxy";
            }
            config.baseUrl = preset.baseUrl;
            config.model = preset.models.length > 0 ? preset.models[0] : "";
            return config;
        }

        JSONObject toJson() throws Exception {
            JSONObject obj = new JSONObject();
            obj.put("providerId", providerId);
            obj.put("presetId", presetId);
            obj.put("name", name);
            obj.put("baseUrl", baseUrl);
            obj.put("apiKey", apiKey);
            obj.put("model", model);
            obj.put("modelOptions", modelOptions);
            return obj;
        }

        static ProviderConfig fromJson(JSONObject obj) {
            String id = obj.optString("providerId", "deepseek");
            String preset = obj.optString("presetId", id.contains(":") ? id.substring(0, id.indexOf(':')) : id);
            ProviderConfig fallback = fromPreset(preset);
            fallback.providerId = id;
            fallback.presetId = preset;
            fallback.name = obj.optString("name", fallback.name);
            fallback.baseUrl = obj.optString("baseUrl", fallback.baseUrl);
            fallback.apiKey = obj.optString("apiKey", "");
            fallback.model = obj.optString("model", fallback.model);
            fallback.modelOptions = obj.optString("modelOptions", "");
            return fallback;
        }
    }

    static class Message {
        String id;
        String role;
        String content;
        String reasoningContent;
        boolean reasoningOpen = true;
        boolean loading = false;
        String model;

        Message(String id, String role, String content, String model) {
            this.id = id;
            this.role = role;
            this.content = content;
            this.model = model;
            this.reasoningContent = "";
        }

        JSONObject toJson() throws Exception {
            JSONObject obj = new JSONObject();
            obj.put("id", id);
            obj.put("role", role);
            obj.put("content", content);
            obj.put("reasoningContent", reasoningContent);
            obj.put("reasoningOpen", reasoningOpen);
            obj.put("loading", false);
            obj.put("model", model);
            return obj;
        }

        static Message fromJson(JSONObject obj) {
            Message message = new Message(
                    obj.optString("id"),
                    obj.optString("role"),
                    obj.optString("content"),
                    obj.optString("model", null)
            );
            message.reasoningContent = obj.optString("reasoningContent", "");
            message.reasoningOpen = obj.optBoolean("reasoningOpen", true);
            message.loading = false;
            return message;
        }

        Message copy() {
            Message clone = new Message(id, role, content, model);
            clone.reasoningContent = reasoningContent;
            clone.reasoningOpen = reasoningOpen;
            clone.loading = false;
            return clone;
        }
    }

    static class ImageRef {
        final String src;
        final String label;

        ImageRef(String src, String label) {
            this.src = src;
            this.label = label;
        }
    }

    static class Conversation {
        String id;
        String parentId = "";
        String title;
        String systemPrompt;
        boolean branchesOpen = false;
        long createdAt;
        long updatedAt;
        List<Message> messages = new ArrayList<>();

        JSONObject toJson() throws Exception {
            JSONObject obj = new JSONObject();
            obj.put("id", id);
            obj.put("parentId", parentId == null ? "" : parentId);
            obj.put("title", title);
            obj.put("systemPrompt", systemPrompt == null ? "" : systemPrompt);
            obj.put("branchesOpen", branchesOpen);
            obj.put("createdAt", createdAt);
            obj.put("updatedAt", updatedAt);
            JSONArray arr = new JSONArray();
            for (Message m : messages) arr.put(m.toJson());
            obj.put("messages", arr);
            return obj;
        }

        static Conversation fromJson(JSONObject obj) {
            Conversation c = new Conversation();
            c.id = obj.optString("id");
            c.parentId = obj.optString("parentId", "");
            c.title = obj.optString("title", "新对话");
            c.systemPrompt = obj.has("systemPrompt") ? obj.optString("systemPrompt", "") : null;
            c.branchesOpen = obj.optBoolean("branchesOpen", false);
            c.createdAt = obj.optLong("createdAt", System.currentTimeMillis());
            c.updatedAt = obj.optLong("updatedAt", c.createdAt);
            JSONArray arr = obj.optJSONArray("messages");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject messageJson = arr.optJSONObject(i);
                    if (messageJson != null) c.messages.add(Message.fromJson(messageJson));
                }
            }
            return c;
        }
    }
}
