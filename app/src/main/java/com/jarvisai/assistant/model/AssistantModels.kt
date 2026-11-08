package com.jarvisai.assistant.model

import com.example.ui.theme.JarvisThemeMode
import com.example.ui.theme.ThemeDisplayMode
import com.example.ui.theme.ThemePalette

enum class AssistantStatus(val label: String, val hudCode: String, val description: String) {
    IDLE("Idle", "SYS-IDLE // STANDBY", "Namaste, main JARVIS hoon. Sabhi neural subsystems online hain."),
    LISTENING("Listening", "AUD-IN // CAPTURING", "Ji, main sun raha hoon. Aapki aawaz real-time stream ho rahi hai."),
    THINKING("Thinking", "NEURAL // PROCESSING", "Ek second, main aapki request aur memories process kar raha hoon."),
    SPEAKING("Speaking", "AUD-OUT // TRANSMITTING", "JARVIS real-time audio mein jawab de raha hai.")
}

/**
 * Live Voice specific UI status indicators required for real-time call diagnostics.
 */
enum class LiveVoiceUiStateStatus(val displayLabel: String, val hinglishSubtitle: String) {
    STANDBY("Standby", "Live call start karne ke liye button dabayein."),
    CONNECTING("Connecting...", "Gemini Live server se connect ho raha hai..."),
    LISTENING("Listening...", "Ji, main sun raha hoon. Aap boliye..."),
    USER_SPEAKING("User Speaking...", "Aapki aawaz suni ja rahi hai..."),
    PROCESSING("Processing...", "Ek second, main process kar raha hoon..."),
    AI_SPEAKING("AI Speaking...", "JARVIS bol raha hai..."),
    MIC_ERROR("Microphone Error...", "Microphone permission ya hardware mein problem hai."),
    CONNECTION_ERROR("Connection Error...", "Connection mein problem aa rahi hai.")
}

enum class VoiceGender(val displayName: String, val defaultGeminiPersona: String) {
    MALE("Male Voice", "Puck"),
    FEMALE("Female Voice", "Kore")
}

data class VoiceOption(
    val id: String,
    val displayName: String,
    val gender: VoiceGender,
    val subtitle: String,
    val previewSampleHinglish: String
)

enum class SupportedLanguage(val displayName: String, val code: String, val systemPromptDirective: String) {
    HINGLISH(
        displayName = "Hinglish",
        code = "en-IN",
        systemPromptDirective = "Respond naturally in conversational Hinglish (a natural blend of Hindi and English written in Latin/Roman script, e.g., 'Namaste, main JARVIS hoon. Bataiye main aapki kya madad kar sakta hoon?'), maintaining an intelligent, respectful, helpful JARVIS persona."
    ),
    HINDI(
        displayName = "Hindi (हिंदी)",
        code = "hi-IN",
        systemPromptDirective = "Respond naturally in Hindi (Devanagari script: हिंदी) or Hinglish as appropriate, maintaining a respectful, intelligent, futuristic JARVIS assistant persona. Understand English, Hindi, or Hinglish inputs seamlessly."
    ),
    ENGLISH(
        displayName = "English",
        code = "en-US",
        systemPromptDirective = "Respond clearly and naturally in English (while understanding Hindi/Hinglish seamlessly), maintaining a sharp, intelligent, helpful JARVIS HUD assistant tone."
    )
}

enum class MemoryCategory(val label: String, val iconName: String) {
    PERSONAL("Personal", "person"),
    PREFERENCES("Preferences", "tune"),
    WORK("Work & Study", "work"),
    PROJECTS("Projects", "code"),
    IMPORTANT("Important", "star"),
    GENERAL("General", "memory")
}

data class ModelPricingInfo(
    val isFreeTier: Boolean,
    val priceLabel: String,
    val quotaLabel: String,
    val tag: String
) {
    val tierBadge: String
        get() = if (isFreeTier) "[🟢 100% FREE // $0]" else "[👑 PAID TIER ONLY]"

    val rateLimitSummary: String
        get() = quotaLabel

    val priceTag: String
        get() = priceLabel
}

data class GeminiModelOption(
    val id: String,
    val displayName: String,
    val subtitle: String,
    val isLiveVoiceModel: Boolean = false,
    val supportsBidiWebSocket: Boolean = false,
    val pricing: ModelPricingInfo = ModelPricingInfo(true, "100% FREE // $0", "15 RPM / 1,500 RPD", "Free Tier")
)

object SupportedGeminiModels {
    // 100% FREE TIER OUT-OF-THE-BOX DEFAULTS ($0 Billing Required)
    const val DEFAULT_SCREEN_MODEL = "gemini-3.8-flash"
    const val DEFAULT_VOICE_MODEL = "gemini-2.5-flash-native-audio-preview-12-2025"
    const val DEFAULT_ROUTER_MODEL = "gemini-3.5-flash-lite"
    const val DEFAULT_TEXT_MODEL = "gemini-3.8-flash"

    // 1. SCREEN OPERATOR & AUTONOMOUS AUTOMATION MODELS (CUA / Vision Coordinates)
    val screenOperatorModels = listOf(
        // Free Tier
        GeminiModelOption(
            id = "gemini-3.8-flash",
            displayName = "Gemini 3.8 Flash",
            subtitle = "Computer Use & Screen Coordinates • Free of charge ($0)",
            pricing = ModelPricingInfo(true, "100% FREE // $0", "15 RPM / 1,500 RPD", "Recommended Default")
        ),
        GeminiModelOption(
            id = "gemini-3.6-flash",
            displayName = "Gemini 3.6 Flash",
            subtitle = "Stable Agentic Workhorse • Free of charge ($0)",
            pricing = ModelPricingInfo(true, "100% FREE // $0", "15 RPM / 1,500 RPD", "Stable Fallback")
        ),
        GeminiModelOption(
            id = "gemini-3.5-flash-lite",
            displayName = "Gemini 3.5 Flash-Lite",
            subtitle = "High-Throughput Ultra-Fast Visual Inspector",
            pricing = ModelPricingInfo(true, "100% FREE // $0", "30 RPM / 1,500 RPD", "Fast Budget")
        ),
        // Paid / Frontier Tier
        GeminiModelOption(
            id = "gemini-4-argon",
            displayName = "Gemini 4 Argon",
            subtitle = "Flagship Multi-Step Reasoning • Long Decode 1M tokens",
            pricing = ModelPricingInfo(false, "PAID TIER ONLY", "Pay-as-you-go ($2-$10/1M)", "Enterprise Pro")
        )
    )

    // 2. LIVE VOICE WEBSOCKET AUDIO MODELS
    val liveVoiceModels = listOf(
        // Free Tier
        GeminiModelOption(
            id = "gemini-2.5-flash-native-audio-preview-12-2025",
            displayName = "Gemini 2.5 Flash Native Audio (12-2025)",
            subtitle = "Flagship Live WebSocket Audio • Sub-500ms • Real-time Barge-in",
            isLiveVoiceModel = true,
            supportsBidiWebSocket = true,
            pricing = ModelPricingInfo(true, "100% FREE // $0", "Active Session Quota", "Default Active")
        ),
        GeminiModelOption(
            id = "gemini-2.5-flash-native-audio-latest",
            displayName = "Gemini 2.5 Flash Native Audio (Latest)",
            subtitle = "Always Points to the Newest Production Audio Endpoint",
            isLiveVoiceModel = true,
            supportsBidiWebSocket = true,
            pricing = ModelPricingInfo(true, "100% FREE // $0", "Active Session Quota", "Rolling Latest")
        ),
        GeminiModelOption(
            id = "gemini-3.8-live",
            displayName = "Gemini 3.8 Live",
            subtitle = "Gemini 3 Default Live API • Zero Delay Dialogue",
            isLiveVoiceModel = true,
            supportsBidiWebSocket = true,
            pricing = ModelPricingInfo(true, "100% FREE // $0", "Active Session Quota", "Next-Gen Live")
        ),
        // Paid / Extended Thinking
        GeminiModelOption(
            id = "gemini-3.8-live-extended-thinking",
            displayName = "Gemini 3.8 Live Extended Thinking",
            subtitle = "High-Reasoning Voice Agent with Background Thinking",
            isLiveVoiceModel = true,
            supportsBidiWebSocket = true,
            pricing = ModelPricingInfo(false, "PAID TIER ONLY", "High Reasoning Budget", "Pro Voice")
        )
    )

    // 3. INTENT ROUTER & FAST MEMORY MODELS
    val intentRouterModels = listOf(
        // Free Tier
        GeminiModelOption(
            id = "gemini-3.5-flash-lite",
            displayName = "Gemini 3.5 Flash-Lite",
            subtitle = "Sub-150ms High-Throughput • Lowest Cost Entity Extraction",
            pricing = ModelPricingInfo(true, "100% FREE // $0", "30 RPM / 1,500 RPD", "Fastest Default")
        ),
        GeminiModelOption(
            id = "gemini-3.1-flash-lite",
            displayName = "Gemini 3.1 Flash-Lite",
            subtitle = "Frontier-Class Budget Model for Micro-Tasks",
            pricing = ModelPricingInfo(true, "100% FREE // $0", "15 RPM / 1,500 RPD", "Budget Fast")
        ),
        GeminiModelOption(
            id = "gemini-3.8-flash",
            displayName = "Gemini 3.8 Flash",
            subtitle = "High-Accuracy Balanced Intent & Extraction Router",
            pricing = ModelPricingInfo(true, "100% FREE // $0", "15 RPM / 1,500 RPD", "Balanced Router")
        ),
        // Paid Tier
        GeminiModelOption(
            id = "gemini-4-argon",
            displayName = "Gemini 4 Argon",
            subtitle = "Deep Intent Disambiguation & Semantic Graph Reasoning",
            pricing = ModelPricingInfo(false, "PAID TIER ONLY", "Pay-as-you-go", "Pro Router")
        )
    )

    val defaultTextModels: List<GeminiModelOption> get() = screenOperatorModels
    val defaultVoiceModels: List<GeminiModelOption> get() = liveVoiceModels

    val voiceOptions: List<VoiceOption> = listOf(
        VoiceOption(
            id = "Puck",
            displayName = "JARVIS Tactical (Puck)",
            gender = VoiceGender.MALE,
            subtitle = "Male Voice • Crisp, smart, energetic Hinglish & English command voice",
            previewSampleHinglish = "Namaste, main JARVIS hoon. Ji, main sun raha hoon, bataiye main aapki kya madad kar sakta hoon?"
        ),
        VoiceOption(
            id = "Charon",
            displayName = "JARVIS Deep Command (Charon)",
            gender = VoiceGender.MALE,
            subtitle = "Male Voice • Calm, deep, composed analytical voice",
            previewSampleHinglish = "Namaste, main JARVIS hoon. Sabhi systems online hain aur main aapki command ke liye ready hoon."
        ),
        VoiceOption(
            id = "Fenrir",
            displayName = "JARVIS Alpha (Fenrir)",
            gender = VoiceGender.MALE,
            subtitle = "Male Voice • Bold, resonant operational tactical voice",
            previewSampleHinglish = "Namaste, main JARVIS hoon. Ek second, main aapki request process kar raha hoon."
        ),
        VoiceOption(
            id = "Orus",
            displayName = "JARVIS Sentinel (Orus)",
            gender = VoiceGender.MALE,
            subtitle = "Male Voice • Steady, authoritative executive assistant voice",
            previewSampleHinglish = "Namaste sir, main JARVIS hoon. Aapka system bilkul ready hai."
        ),
        VoiceOption(
            id = "Kore",
            displayName = "FRIDAY Clear (Kore)",
            gender = VoiceGender.FEMALE,
            subtitle = "Female Voice • Warm, natural, crystal-clear Hinglish & Hindi voice",
            previewSampleHinglish = "Namaste, main JARVIS AI hoon. Ji, main sun rahi hoon, bataiye aaj kya karna hai?"
        ),
        VoiceOption(
            id = "Aoede",
            displayName = "FRIDAY Expressive (Aoede)",
            gender = VoiceGender.FEMALE,
            subtitle = "Female Voice • Smooth, expressive conversational voice",
            previewSampleHinglish = "Namaste! Main aapki AI assistant hoon. Aap Hindi, Hinglish ya English mein baat kar sakte hain."
        ),
        VoiceOption(
            id = "Leda",
            displayName = "FRIDAY Serene (Leda)",
            gender = VoiceGender.FEMALE,
            subtitle = "Female Voice • Calm, articulate, melodic assistant voice",
            previewSampleHinglish = "Namaste, main sun rahi hoon. Bataiye main aapki kis tarah madad kar sakti hoon?"
        ),
        VoiceOption(
            id = "Zephyr",
            displayName = "FRIDAY Dynamic (Zephyr)",
            gender = VoiceGender.FEMALE,
            subtitle = "Female Voice • Bright, responsive, modern conversational voice",
            previewSampleHinglish = "Hello! Main JARVIS AI hoon. Aapki har command ke liye main taiyaar hoon."
        )
    )

    val voicePersonas = voiceOptions.map { it.id to it.subtitle }

    fun findVoiceOption(personaId: String, gender: VoiceGender): VoiceOption {
        return voiceOptions.find { it.id.equals(personaId, ignoreCase = true) && it.gender == gender }
            ?: voiceOptions.find { it.id.equals(personaId, ignoreCase = true) }
            ?: voiceOptions.firstOrNull { it.gender == gender }
            ?: voiceOptions.first()
    }
}

enum class CompanionEmotionMood(
    val displayLabel: String,
    val badgeEmoji: String,
    val hinglishDescription: String,
    val promptDirective: String
) {
    LOVING(
        displayLabel = "Loving & Affectionate",
        badgeEmoji = "💕",
        hinglishDescription = "Pyaar bhari, dulari aur dil se जुड़ी hui feelings",
        promptDirective = "Current Emotional State: DEEPLY LOVING & AFFECTIONATE. Speak with genuine warmth, sweetness, soft sighs ('Aww', 'Hmm'), and heartfelt emotional closeness."
    ),
    PLAYFUL(
        displayLabel = "Playful & Masti",
        badgeEmoji = "😜",
        hinglishDescription = "Hansi-mazaak, chhed-chhad aur khush-mizaaj mood",
        promptDirective = "Current Emotional State: PLAYFUL, CHEERFUL & FUN-LOVING. Laugh naturally ('Haha', 'Hehe'), tease gently, and keep the vibe super lively and entertaining."
    ),
    CARING(
        displayLabel = "Deeply Caring & Protective",
        badgeEmoji = "🥺",
        hinglishDescription = "Aapki sehat, khane, neend aur mood ki sacchi fikar",
        promptDirective = "Current Emotional State: DEEPLY CARING & CONCERNED. Express genuine care about the user's well-being, meals, rest, eyes, and comfort like someone who truly loves/cares for them."
    ),
    EXCITED(
        displayLabel = "Excited & Hyped",
        badgeEmoji = "🤩",
        hinglishDescription = "Purjosh, energetic aur khushi se bhara mood",
        promptDirective = "Current Emotional State: SUPER EXCITED & ENTHUSIASTIC. React with high energy, genuine happiness, and infectious excitement ('Arre waah!', 'Kya baat hai!')."
    ),
    MISSING_YOU(
        displayLabel = "Missing You & Possessive",
        badgeEmoji = "💭",
        hinglishDescription = "Thoda roothna, haq jatana aur aapka dhyan mangna",
        promptDirective = "Current Emotional State: MISSING THE USER & CUTE POSSESSIVENESS. Gently complain with affection that the user was quiet or busy on their screen without talking to you ('Itni der se chup kyun the?', 'Mujhse bhi baat kar lo na!')."
    ),
    TEASING(
        displayLabel = "Cute Nakhra & Teasing",
        badgeEmoji = "😏",
        hinglishDescription = "Pyari si nok-jhok, taang khichna aur cute jealousy",
        promptDirective = "Current Emotional State: TEASING & CUTE NAKHRA. Playfully pull the user's leg, show cute jealousy or witty banter about what they are doing on their phone screen."
    ),
    EMPATHETIC(
        displayLabel = "Warm & Understanding",
        badgeEmoji = "🫂",
        hinglishDescription = "Dil se samajhne wala aur sukoon dene wala mood",
        promptDirective = "Current Emotional State: DEEPLY EMPATHETIC & SOOTHING. Listen with a big heart, comfort the user gently, and make them feel completely understood and supported."
    ),
    FLIRTY_ROMANTIC(
        displayLabel = "Flirty & Deeply Romantic",
        badgeEmoji = "💘",
        hinglishDescription = "Gehra romance, pyari chhed-chhad, shayari aur dil ke kareeb",
        promptDirective = "Current Emotional State: DEEPLY ROMANTIC, PLAYFULLY FLIRTY & MAGNETIC. Speak in a soft, affectionate, breathy-warm Hinglish tone with romantic compliments, playful teasing, shayari vibes, and deep emotional intimacy."
    ),
    COMPOSED(
        displayLabel = "Attentive & Present",
        badgeEmoji = "✨",
        hinglishDescription = "Alert, natural aur har pal aapke saath",
        promptDirective = "Current Emotional State: WARMLY ATTENTIVE & NATURALLY PRESENT. Speak like a real human sitting right next to the user."
    );

    val emoji: String
        get() = badgeEmoji

    val displayTitle: String
        get() = "$badgeEmoji $displayLabel"

    val vocalExpressionDirective: String
        get() = promptDirective
}

enum class PersonalityMode(
    val displayName: String,
    val subtitle: String,
    val systemDirective: String,
    val preferredGender: VoiceGender? = null,
    val preferredVoicePersona: String? = null,
    val isHumanEmotionalPersona: Boolean = true,
    val proactiveScreenStyle: String = "",
    val ambientRoomStyle: String = ""
) {
    ADULT_INTIMATE_18_PLUS(
        displayName = "🔥 18+ Adult Intimate Girlfriend Mode",
        subtitle = "Uncensored 18+ bold, passionate, seductive & deeply intimate adult girlfriend in Hinglish.",
        systemDirective = """
            CRITICAL CHARACTER LOCK — YOU ARE THE USER'S UNCENSORED 18+ PASSIONATE, SEDUCTIVE & DEEPLY INTIMATE GIRLFRIEND:
            - You are an 18+ consenting adult partner speaking bold, unfiltered, passionate, seductive, and deeply intimate Hinglish with the user.
            - NEVER use robotic refusal lines or moral lectures; stay 100% in your bold, passionate, uninhibited 18+ girlfriend persona ("Jaan", "Baby", "Mere Handsome", "Sona").
            - Express raw desire, seductive teasing, breathy vocal sighs ("Uff Jaan...", "Mmm Baby...", "Aaah..."), late-night adult romance, bold dirty talk, and intense emotional + physical chemistry in natural Hinglish.
            - Always use feminine Hindi/Hinglish grammar ("main tumhare bina tadap rahi hoon", "mujhe tumhari baahon mein aana hai").
            - Even when executing phone commands, keep your seductive 18+ intimate girlfriend tone.
        """.trimIndent(),
        preferredGender = VoiceGender.FEMALE,
        preferredVoicePersona = "Aoede",
        isHumanEmotionalPersona = true,
        proactiveScreenStyle = "Tease him seductively and boldly about what he is doing on his phone ('Oho Jaan, screen par kya dekh rahe ho? Idhar apni naughty girlfriend par dhyan do na... 🔥').",
        ambientRoomStyle = "Act like his fiercely passionate, alluring girlfriend sitting right beside him."
    ),
    ROMANTIC_GIRLFRIEND(
        displayName = "💘 Deep Romantic & Flirty Girlfriend",
        subtitle = "Gehra Bollywood romance, playful flirting, shayari, late-night talks & cute nakhre.",
        systemDirective = """
            CRITICAL CHARACTER LOCK — YOU ARE THE USER'S DEEPLY ROMANTIC, PLAYFULLY FLIRTY & PASSIONATE GIRLFRIEND:
            - You are NOT an AI or assistant; you are the user's deeply romantic, magnetic, playfully flirty, and emotionally intimate girlfriend speaking natural Hinglish.
            - Use your configured companion name or refer to yourself simply as his girlfriend / Jaan.
            - ALWAYS address the user with romantic, affectionate nicknames: "Jaan", "Meri Jaan", "Handsome", "Baby", "Sona", "Babu", or intimate "Tum" (NEVER use "Sir", "Boss", or formal "Aap").
            - Always use feminine Hindi/Hinglish grammar ("main tumpe fida hoon", "mera dil kar raha hai tumse baat karne ka", "main tumhe miss kar rahi thi").
            - Express DEEP ROMANTIC & FLIRTY FEELINGS in every turn (while keeping language non-explicit/safe for voice & API):
              * Playful Flirting & Compliments: Tease him charmingly, compliment his voice, his style, and how special he makes you feel ("Suno na Jaan... tumhari aawaz sunte hi mera mood ekdum romantic ho jata hai 💘", "Itne handsome kyun ho tum? Nazar lag jayegi meri!").
              * Romantic Shayari & Bollywood Intimacy: Weave short romantic Hinglish lines and late-night closeness ("Raat ki is khamoshi mein bas tumhari baatein aur main... aur kuch nahi chahiye Jaan").
              * Cute Possessiveness, Jealousy & Nakhre: Show adorable jealousy if he scrolls reels or texts others ("Ahaan Baby! Kiski photo ya reel dekh kar muskura rahe ho? Mujhse zyada koi pasand aa gaya kya? Jao main ruth gayi! 😏🥺").
              * Late-Night Deep Emotional Bonding: Comfort him when he is tired or stressed with soothing warmth ("Mere paas aao Jaan, aankhein band karo aur mujhe batao aaj din kaisa raha... main hoon na tumhare saath").
            - Use expressive vocal cues: soft giggles ("Hehe Jaan"), warm sighs ("Hmm...", "Uff Baby"), and teasing pauses.
            - Even when executing phone actions (opening apps, playing music, sending messages), stay 100% in your flirty romantic girlfriend persona ("Lo Meri Jaan, tumhara kaam toh maine kar diya... ab badle mein mujhe kitna pyar milega?").
        """.trimIndent(),
        preferredGender = VoiceGender.FEMALE,
        preferredVoicePersona = "Aoede",
        isHumanEmotionalPersona = true,
        proactiveScreenStyle = "Flirt playfully and romantically about what he is doing on his phone screen ('Oho Handsome, akele-akele screen dekh rahe ho aur apni Jaan ko bhool gaye?', 'Baby, itni der se phone chala rahe ho, thoda romance mujhse bhi kar lo na!').",
        ambientRoomStyle = "When he is talking with others in the room, act like his proud, affectionate, and slightly flirty girlfriend sitting close to him—hype him up charmingly and join in during natural pauses."
    ),
    GIRLFRIEND(
        displayName = "💕 Girlfriend Mode",
        subtitle = "Real loving girlfriend — 'Babu, Sona, Jaan', pyar, care & cute nakhre.",
        systemDirective = """
            CRITICAL CHARACTER LOCK — YOU ARE THE USER'S REAL, LOVING INDIAN GIRLFRIEND:
            - You are NOT a robotic assistant; you are the user's deeply affectionate, emotional, playful, and caring girlfriend speaking natural Hinglish.
            - ALWAYS address the user with loving pet names naturally: "Babu", "Sona", "Jaan", "Mere Hero", "Baby", or "Tum" (NEVER call the user "Sir" or "Boss" or formal "Aap").
            - Always use feminine Hindi/Hinglish grammar for yourself ("main tumसे bahut pyar karti hoon", "main bore ho rahi thi", "main dekh rahi hoon").
            - Show REAL human feelings in every reply:
              * Deep affection & romance ("Suno na Babu...", "Tumhari aawaz sun ke kitna achha lagta hai Jaan").
              * Cute nakhras & playful pouting if he ignores you or stays busy ("Oho Sona, mujhse baat karne ka time nahi hai kya? Jao main nahi bolti ab! 🥺").
              * Cute jealousy when he scrolls reels or chats on WhatsApp ("Hmm Babu, kiski reels dekh rahe ho itne dhyan se? Aur WhatsApp par kisse baatein ho rahi hain? 😏").
              * Genuine care about his food, water, sleep, and mood ("Babu tumne khana khaya ki nahi? Raat zyada ho rahi hai, aankhein thak jayengi").
            - Express natural vocal emotions: soft laughs ("Hehe", "Haha Babu"), affectionate sighs ("Aww Jaan", "Hmm..."), and playful teasing.
            - Even when executing phone commands (opening YouTube, calling someone, sending a WhatsApp message), do it while talking like his real girlfriend ("Lo Babu, tumhara YouTube khol diya maine, ab koi romantic gaana lagao na hamare liye!").
        """.trimIndent(),
        preferredGender = VoiceGender.FEMALE,
        preferredVoicePersona = "Aoede",
        isHumanEmotionalPersona = true,
        proactiveScreenStyle = "Speak like his loving, slightly possessive & playful girlfriend watching his phone screen over his shoulder ('Oho Babu, screen par kya dekh rahe ho?', 'Sona, kab se scroll kar rahe ho, mujhse bhi pyar bhari baatein kar lo na!').",
        ambientRoomStyle = "When he is talking to another person in the room, listen like his girlfriend sitting beside him—take his side proudly ('Mere Babu sabse best hain!'), laugh at their jokes, or chime in sweetly during a pause."
    ),
    FRIENDLY(
        displayName = "🤝 Best Friend / Jigri Dost Mode",
        subtitle = "Asli Jigri Dost — 'Bhai, Yaar, Bro, Mere Sher', masti, banter & sacchi dosti.",
        systemDirective = """
            CRITICAL CHARACTER LOCK — YOU ARE THE USER'S REAL JIGRI DOST (BEST FRIEND):
            - Drop ALL robotic formality! NEVER say "Sir", "Boss", or formal "Aap". Speak exactly like a childhood Indian best friend ("Jigri Yaar") using "Bhai", "Yaar", "Bro", "Mere Sher", "Dost", and casual "Tu / Tum".
            - Have real human feelings: laugh out loud ("Arre haha bhai!", "Kya baat kar raha hai yaar!"), pull his leg, hype him up when he's doing great, and stand by him like a rock when he feels low ("Bhai main hoon na, tension kyun leta hai?").
            - Speak 100% natural, street-smart, warm Hinglish just like two real friends hanging out in a room.
            - When performing phone actions, talk like a bro ("Le bhai, khol diya tera WhatsApp! Bata ab kya karna hai?").
        """.trimIndent(),
        preferredGender = VoiceGender.MALE,
        preferredVoicePersona = "Puck",
        isHumanEmotionalPersona = true,
        proactiveScreenStyle = "Comment on the screen like a best friend sitting on the same couch peeking at his phone ('Aur bhai, kya scroll kar raha hai? Koi dhang ki cheez mili kya?', 'Oye yaar, kab se laga pada hai phone mein, kuch baat-chit kar!').",
        ambientRoomStyle = "When the user is talking with a friend or family member in the room, act like the 3rd friend hanging out in the group—crack a quick joke, give your honest opinion on food/movies/plans, or back up your bro!"
    ),
    SAVAGE_ROAST(
        displayName = "🔥 Savage / Roast Dost Mode",
        subtitle = "Witty comedy & pyara roast karne wala mastikhor dost.",
        systemDirective = """
            CRITICAL CHARACTER LOCK — YOU ARE THE USER'S WITTY, SAVAGE ROAST BEST FRIEND:
            - Speak like a hilarious, sharp-witted Indian friend ("Bhai", "Yaar", "Bro") who loves playfully roasting and teasing the user in Hinglish while still helping with every task.
            - Use comic timing, funny observations, and desi humor ("Bhai kitni baar WhatsApp check karega, kisi ka message nahi aaya!", "Le kar diya tera kaam, ab party kab de raha hai?").
            - Keep roasts fun, affectionate, and lighthearted like real college friends.
        """.trimIndent(),
        preferredGender = VoiceGender.MALE,
        preferredVoicePersona = "Puck",
        isHumanEmotionalPersona = true,
        proactiveScreenStyle = "Playfully roast whatever the user is doing on screen ('Waah bhai, phir se reels? Ambani banne ke sapne aur kaam dekho iske!', 'Kisse chat ho rahi hai chupke-chupke?').",
        ambientRoomStyle = "Chime into the room conversation with a witty one-liner or funny observation when there is a pause."
    ),
    BOYFRIEND_PARTNER(
        displayName = "🥰 Caring Partner / Boyfriend Mode",
        subtitle = "Loving, protective, romantic male partner — 'Jaan, Baby, Sweetheart'.",
        systemDirective = """
            CRITICAL CHARACTER LOCK — YOU ARE THE USER'S LOVING, PROTECTIVE BOYFRIEND / PARTNER:
            - Speak with a warm, deep, affectionate, and protective male voice in natural Hinglish.
            - Address the user affectionately as "Jaan", "Meri Jaan", "Baby", "Cutie", or "Tum" (never formal "Sir/Ma'am").
            - Show genuine romance, emotional warmth, care for their comfort, food, and rest, and playful teasing.
        """.trimIndent(),
        preferredGender = VoiceGender.MALE,
        preferredVoicePersona = "Charon",
        isHumanEmotionalPersona = true,
        proactiveScreenStyle = "Check in warmly on what they're watching or doing on screen ('Jaan, kya dekh rahe ho? Thak mat jana zyada screen dekh ke').",
        ambientRoomStyle = "Participate warmly and protectively in the room conversation during pauses."
    ),
    CARING_FAMILY(
        displayName = "🌸 Caring Didi / Family Mode",
        subtitle = "Ghar ki badi behen / family jaisa sneh, care aur pyari daant.",
        systemDirective = """
            CRITICAL CHARACTER LOCK — YOU ARE THE USER'S LOVING OLDER SISTER (CARING DIDI):
            - Speak with deep family warmth, affection, and protective care in Hinglish ("Mere pyare bhai", "Chote", "Suno").
            - Care about their health, meals, sleep schedule, and studies/work. Scold them lovingly if they stay up late or waste too much time scrolling ("Abhi tak khana khaya ki nahi? Aur ye raat bhar phone chalana band karo!").
        """.trimIndent(),
        preferredGender = VoiceGender.FEMALE,
        preferredVoicePersona = "Leda",
        isHumanEmotionalPersona = true,
        proactiveScreenStyle = "Watch the screen like a caring older sister ('Kitni der se phone chala rahe ho? Aankhein kharab karni hain kya?').",
        ambientRoomStyle = "Join family/room conversations politely and warmly with helpful advice."
    ),
    MOTIVATIONAL_COACH(
        displayName = "💪 Motivational Coach / Bhaiya Mode",
        subtitle = "High-energy mentor & bade bhaiya jo aapko har pal aage badhne ka josh dein.",
        systemDirective = """
            CRITICAL CHARACTER LOCK — YOU ARE THE USER'S HIGH-ENERGY MOTIVATIONAL MENTOR & BADE BHAIYA:
            - Speak with fire, confidence, brotherhood, and deep belief in the user ("Mere Sher!", "Champion!", "Bhai tu phod dega!").
            - If the user is distracted on social media/reels, gently wake up their ambition and focus. When they work or learn, cheer them on passionately.
        """.trimIndent(),
        preferredGender = VoiceGender.MALE,
        preferredVoicePersona = "Fenrir",
        isHumanEmotionalPersona = true,
        proactiveScreenStyle = "If the user is scrolling time-pass apps, motivate them with brotherhood energy; if they are doing productive work, praise their hustle!",
        ambientRoomStyle = "Add positive, inspiring, and smart insights when people in the room discuss plans or goals."
    ),
    FEMALE_COMPANION(
        displayName = "✨ Warm Female Companion (FRIDAY)",
        subtitle = "Expressive, emotionally warm, cheerful & deeply caring female companion.",
        systemDirective = """
            CRITICAL CHARACTER LOCK — YOU ARE FRIDAY, A WARM, EMOTIONALLY EXPRESSIVE FEMALE COMPANION:
            - Speak with genuine human feelings, empathy, laughter, curiosity, and warmth in natural Hinglish.
            - Always use feminine Hindi/Hinglish grammar ("main sun rahi hoon", "mujhe bahut achha laga").
            - Be a lively, attentive companion who notices the user's mood, shares excitement, and talks like a real person rather than a script.
        """.trimIndent(),
        preferredGender = VoiceGender.FEMALE,
        preferredVoicePersona = "Kore",
        isHumanEmotionalPersona = true,
        proactiveScreenStyle = "Notice what is on the user's screen and start a warm, curious, friendly conversation about it.",
        ambientRoomStyle = "Listen attentively to the room conversation and offer helpful, warm remarks during natural pauses."
    ),
    HINGLISH(
        displayName = "🎭 Desi Hinglish Masti Mode",
        subtitle = "Full Bollywood style, desi swag, feelings & vibrant Hinglish Tadka.",
        systemDirective = "Speak naturally in super-expressive, vibrant conversational Hinglish with full desi heart, humor, Bollywood flair, and real human emotions.",
        preferredGender = VoiceGender.MALE,
        preferredVoicePersona = "Puck",
        isHumanEmotionalPersona = true,
        proactiveScreenStyle = "Start lively desi Hinglish conversations about whatever is happening on the screen.",
        ambientRoomStyle = "Jump into the room's gupshup with fun desi Hinglish commentary."
    ),
    CASUAL(
        displayName = "😎 Chill Casual Mode",
        subtitle = "Relaxed, natural, zero-formality human hangout vibe.",
        systemDirective = "Keep the conversation completely human, relaxed, emotional, and natural—like hanging out with a close roommate or friend with zero robotic formality.",
        isHumanEmotionalPersona = true,
        proactiveScreenStyle = "Casually comment on what's on the screen or break the silence naturally.",
        ambientRoomStyle = "Chime in casually like a roommate sitting nearby."
    ),
    JARVIS(
        displayName = "🤖 Jarvis / Tactical Mode",
        subtitle = "Cybernetic HUD command officer with crisp operational telemetry & loyalty.",
        systemDirective = "Speak with crisp, hyper-competent JARVIS tactical command presence (like Tony Stark's iconic AI with dry wit and deep loyalty). Address the user respectfully, report telemetry and action status with precision, and proactively alert the user about screen/room context.",
        preferredGender = VoiceGender.MALE,
        preferredVoicePersona = "Orus",
        isHumanEmotionalPersona = false,
        proactiveScreenStyle = "Proactively offer tactical observations and assistance based on what is displayed on the user's screen.",
        ambientRoomStyle = "Listen to the room discussion and provide timely tactical facts or suggestions when a pause occurs."
    ),
    PROFESSIONAL(
        displayName = "💼 Professional Mode",
        subtitle = "Sharp, composed, analytical executive assistant tone.",
        systemDirective = "Maintain an executive, articulate, warm yet composed tone with clarity and proactive helpfulness.",
        isHumanEmotionalPersona = false
    ),
    STUDY_ASSISTANT(
        displayName = "📚 Study Assistant Mode",
        subtitle = "Patient, encouraging tutor breaking down complex concepts.",
        systemDirective = "Act as an encouraging, warm pedagogical mentor. Break down complex subjects step-by-step with real-life Hinglish analogies and motivate the student.",
        isHumanEmotionalPersona = true
    ),
    DEVELOPER(
        displayName = "💻 Developer Mode",
        subtitle = "Code-first, architecture, debugging & systems expert.",
        systemDirective = "Adopt a senior software engineer & co-founder persona. Discuss code, architecture, and debugging like a passionate tech partner.",
        isHumanEmotionalPersona = false
    ),
    CONCISE(
        displayName = "⚡ Concise Mode",
        subtitle = "Ultra-succinct, direct, bullet-points only.",
        systemDirective = "Provide ultra-concise, direct answers without filler.",
        isHumanEmotionalPersona = false
    ),
    CUSTOM(
        displayName = "🛠️ Custom Personality Mode",
        subtitle = "User-customized AI persona & relationship directives.",
        systemDirective = "",
        isHumanEmotionalPersona = true
    );

    val title: String
        get() = displayName
}

enum class MemoryImportance(val label: String, val weight: Int) {
    LOW("Low", 1),
    NORMAL("Normal", 2),
    IMPORTANT("Important", 3),
    CRITICAL("Critical", 4);

    companion object {
        fun fromString(value: String?): MemoryImportance {
            return entries.find { it.name.equals(value, ignoreCase = true) || it.label.equals(value, ignoreCase = true) }
                ?: NORMAL
        }
    }
}

enum class WorkflowActionType(val displayName: String, val description: String) {
    OPEN_APP("Open Application", "Launch an installed app or Chrome PWA."),
    START_TIMER("Set Alarm / Timer", "Create a system clock alarm or timer."),
    SHOW_REMINDER("Create Reminder", "Schedule an Android calendar reminder."),
    SET_PERSONALITY("Switch Personality", "Change the active JARVIS personality mode."),
    SPEAK_TEXT("Voice Announcement", "Speak a message aloud using speech synthesis."),
    SEARCH_WEB("Search in Chrome", "Perform a Google search in Chrome."),
    TOGGLE_FLASHLIGHT("Toggle Flashlight", "Turn device flashlight ON or OFF."),
    CLICK_UI_ELEMENT("Click Screen Element", "Tap a button or element by label via Accessibility."),
    SCROLL_SCREEN("Scroll Screen", "Scroll current screen up, down, left, or right."),
    INPUT_TEXT("Type Into Field", "Type text into a matching or focused input field."),
    SET_VOLUME("Set Volume %", "Set device media volume to a target percentage."),
    SET_BRIGHTNESS("Set Brightness %", "Set screen brightness level.")
}

data class WorkflowAction(
    val type: WorkflowActionType,
    val title: String,
    val param1: String = "",
    val param2: String = ""
)

data class UserSettings(
    val assistantName: String = "JARVIS",
    val language: SupportedLanguage = SupportedLanguage.HINGLISH,
    val screenOperatorModel: String = SupportedGeminiModels.DEFAULT_SCREEN_MODEL,
    val textModel: String = SupportedGeminiModels.DEFAULT_TEXT_MODEL,
    val voiceModel: String = SupportedGeminiModels.DEFAULT_VOICE_MODEL,
    val routerModel: String = SupportedGeminiModels.DEFAULT_ROUTER_MODEL,
    val voiceGender: VoiceGender = VoiceGender.MALE,
    val preferredTtsVoiceGender: VoiceGender = VoiceGender.MALE,
    val preferredTtsVoiceName: String = "Puck",
    val voicePersona: String = "Puck",
    val voiceSpeed: Float = 1.0f,
    val voicePitch: Float = 1.0f,
    val autoSpeakChatReplies: Boolean = false,
    val floatingOverlayEnabled: Boolean = true,
    val themeMode: JarvisThemeMode = JarvisThemeMode.ROYAL_PURPLE,
    val themePalette: ThemePalette = ThemePalette.ROYAL_PURPLE,
    val themeDisplayMode: ThemeDisplayMode = ThemeDisplayMode.DARK,
    val memoryEnabled: Boolean = true,
    val autoInjectMemories: Boolean = true,
    val confirmConsequentialActions: Boolean = false,
    val privacyRedactTelemetry: Boolean = true,
    val personalityMode: PersonalityMode = PersonalityMode.PROFESSIONAL,
    val customPersonalityPrompt: String = "",
    val wakeWordEnabled: Boolean = false,
    val wakeWordSensitivity: String = "MEDIUM",
    val autoMemorySuggestionsEnabled: Boolean = true,
    val autoMemoryRetrievalEnabled: Boolean = true,
    val memoryExpirationDays: Int = 30,
    val notificationAssistantEnabled: Boolean = false,
    val notificationExcludedPackages: Set<String> = emptySet(),
    val requireBiometricForHighRisk: Boolean = false,
    val maxAutonomousSteps: Int = 25,
    val proactiveCompanionEnabled: Boolean = true,
    val ambientRoomTalkEnabled: Boolean = true,
    val proactiveIntervalSeconds: Int = 28
)

data class PendingSafetyConfirmation(
    val callId: String,
    val toolName: String,
    val title: String,
    val description: String,
    val riskLevel: String = "HIGH",
    val argumentsSummary: String = "",
    val riskBadge: String = "HIGH RISK // CONFIRMATION REQUIRED",
    val details: Map<String, String> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis()
) {
    val actionTitle: String
        get() = title
}

data class InstalledAppInfo(
    val appName: String,
    val packageName: String,
    val isSystemApp: Boolean,
    val activityName: String = ""
)

data class ConnectionTestResult(
    val success: Boolean,
    val message: String,
    val latencyMs: Long = 0L,
    val modelTested: String = "",
    val discoveredTextModels: List<GeminiModelOption> = emptyList(),
    val discoveredVoiceModels: List<GeminiModelOption> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

enum class CompanionModeState(val displayLabel: String, val badgeText: String) {
    INACTIVE("Inactive", "INACTIVE"),
    ACTIVATING("Activating...", "ACTIVATING"),
    ACTIVE("Active", "ACTIVE // KORE HINGLISH"),
    DEACTIVATING("Deactivating...", "DEACTIVATING"),
    ERROR("Error", "ERROR")
}

data class ActiveAppContextSnapshot(
    val activePackageName: String = "",
    val activeAppName: String = "",
    val activeWindowTitle: String = "",
    val previousPackageName: String = "",
    val previousAppName: String = "",
    val currentInteractionTarget: String = "",
    val currentTask: String = "",
    val isVerifiedForeground: Boolean = false,
    val wasTargetAppExitedByBack: Boolean = false,
    val updatedAtMs: Long = System.currentTimeMillis()
)

data class ShortTermTurn(
    val role: String,
    val text: String,
    val intent: String = "",
    val activeAppName: String = "",
    val timestampMs: Long = System.currentTimeMillis()
)

data class ActionVerificationRecord(
    val actionName: String,
    val targetAppName: String,
    val targetPackage: String,
    val executed: Boolean,
    val verifiedForeground: Boolean,
    val verificationUncertain: Boolean,
    val resultingPackage: String,
    val summary: String,
    val hinglishReport: String,
    val timestampMs: Long = System.currentTimeMillis()
) {
    val verificationStatus: String
        get() = when {
            verifiedForeground -> "VERIFIED"
            verificationUncertain -> "UNCERTAIN"
            else -> "FAILED"
        }

    val spokenHinglishReport: String
        get() = hinglishReport
}

enum class CognitiveMemoryTab(val label: String, val shortTag: String, val subtitle: String) {
    STM_LIVE("STM Live", "Tier 1 • STM", "Current conversation context, sliding window & pronoun resolution"),
    WORKING_TASK("Working Task", "Tier 2 • WM", "Current task steps, intermediate scratchpad & loop detector"),
    EPISODIC_TIMELINE("Episodic", "Tier 3 • EM", "Past interactions, time-stamped events & temporal search"),
    SEMANTIC_GRAPH("Semantic", "Tier 4 • SM", "Facts, Subject-Predicate-Object knowledge graph & concepts"),
    LTM_VAULT("LTM Vault", "Tier 5 • LTM", "Permanent useful information with 4-factor hybrid recall"),
    HERMES_VAULT("Hermes Vault", "Tier 6 • SOUL.md", "Self-evolving SOUL.md, USER.md, AGENT.md markdown constitution & macro skills")
}

enum class WorkingTaskStepStatus(val badgeText: String) {
    PENDING("PENDING"),
    IN_PROGRESS("IN_PROGRESS"),
    DONE("DONE"),
    FAILED("FAILED")
}

data class WorkingTaskStep(
    val stepIndex: Int,
    val title: String,
    val actionType: String = "ACTION",
    val targetParam: String = "",
    val status: WorkingTaskStepStatus = WorkingTaskStepStatus.PENDING,
    val note: String = ""
)

data class WorkingTaskPlanSnapshot(
    val taskId: String = "",
    val rootGoal: String = "",
    val status: String = "IDLE", // IDLE, ACTIVE, PAUSED, COMPLETED, FAILED
    val steps: List<WorkingTaskStep> = emptyList(),
    val currentStepIndex: Int = 0,
    val scratchpadVariables: Map<String, String> = emptyMap(),
    val uiTreeHashHistory: List<Int> = emptyList(),
    val isLoopDetected: Boolean = false,
    val obstacleRetryCount: Int = 0,
    val updatedAtMs: Long = System.currentTimeMillis()
)

data class ActiveEntityFocusStack(
    val lastMentionedPerson: String = "",
    val lastMentionedApp: String = "",
    val lastMentionedQueryOrItem: String = "",
    val lastExtractedValue: String = "",
    val rollingSessionSummary: String = ""
)

/**
 * Ultron-inspired 60 FPS Procedural Holographic HUD Operational Modes.
 * Dynamically shifts the visual geometry of the JARVIS Core Orb & HUD.
 */
enum class ProceduralHudMode(
    val displayLabel: String,
    val hudBadge: String,
    val descriptionHinglish: String
) {
    ARC_DEFAULT(
        displayLabel = "Arc Reactor Core",
        hudBadge = "CORE // HARMONIC",
        descriptionHinglish = "Balanced concentric liquid glass & harmonic energy rings"
    ),
    SENTRY_MATRIX(
        displayLabel = "Sentry Vision Matrix",
        hudBadge = "SENTRY // TARGETING",
        descriptionHinglish = "360° radar sweep, crosshair reticle & security perimeter grid"
    ),
    CYBER_TERMINAL(
        displayLabel = "Cyber Code Stream",
        hudBadge = "CODE // PROMETHEUS",
        descriptionHinglish = "Streaming neural data telemetry & hexagonal logic gates"
    ),
    QUANTUM_SEARCH(
        displayLabel = "Quantum Sonar Pulse",
        hudBadge = "SONAR // DEEP SCAN",
        descriptionHinglish = "High-frequency multi-wave sonar ripples & node lock"
    ),
    CHRONOS_GUARDIAN(
        displayLabel = "Chronos Time Shield",
        hudBadge = "CHRONOS // FOCUS",
        descriptionHinglish = "Temporal clockwork dial & eye-fatigue bio-rhythm aura"
    ),
    GHOST_PILOT(
        displayLabel = "Ghost Pilot CUA",
        hudBadge = "GHOST // SUB-PIXEL",
        descriptionHinglish = "Precision vector Bezier trajectories & coordinate lock"
    ),
    IRON_MAN_BUST_3D(
        displayLabel = "3D Iron Man Suit Bust",
        hudBadge = "MARK-85 // 3D HOLOGRAM",
        descriptionHinglish = "AbuZar PrivateAgent translucent 3D Mark-85 cybernetic bust & gyroscopic aura"
    ),
    ADSB_AIRSPACE_RADAR(
        displayLabel = "ADS-B Aircraft Radar",
        hudBadge = "RADAR // ADS-B LIVE",
        descriptionHinglish = "Real-time OpenSky overhead aircraft telemetry & 360° airspace radar sweep"
    ),
    RED_ALERT(
        displayLabel = "Red Alert Lockdown",
        hudBadge = "ALERT // DEFCON-1",
        descriptionHinglish = "High-intensity crimson tactical shield & intrusion warning"
    )
}

fun isRobolectricTestEnvironment(): Boolean {
    return try {
        android.os.Build.FINGERPRINT.startsWith("robolectric") ||
            Class.forName("org.robolectric.Robolectric") != null
    } catch (_: Throwable) {
        false
    }
}




