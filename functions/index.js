const { onCall } = require("firebase-functions/v2/https");
const { logger } = require("firebase-functions/v2");
const admin = require('firebase-admin');
const nodemailer = require("nodemailer");
const crypto = require('crypto');

admin.initializeApp();

const transporter = nodemailer.createTransport({
    service: "yahoo",
    auth: {
        user: "reneegithinji@yahoo.com",
        pass: "hpnzhrwgtuiuoxca"
    }
});

// Generate a unique token
function generateToken() {
    return crypto.randomBytes(32).toString('hex');
}

// Helper function to format readable date
function formatReadableDate(timestamp) {
    const date = new Date(timestamp);
    const hours = date.getHours();
    const minutes = date.getMinutes();
    const ampm = hours >= 12 ? 'PM' : 'AM';
    const hour12 = hours % 12 || 12;
    const minuteStr = minutes < 10 ? '0' + minutes : minutes;

    const day = date.getDate();
    const month = date.toLocaleString('default', { month: 'long' });
    const year = date.getFullYear();

    return `${hour12}:${minuteStr} ${ampm} on ${day} ${month} ${year}`;
}

// ==================== FUNCTION 1: CREATE USER DOCUMENT ====================
exports.createUserDocument = onCall(async (request) => {
    const { email, name, role, userId } = request.data;

    if (!email) {
        throw new Error("Email is required");
    }

    // Check if user already exists
    const userDoc = await admin.firestore().collection('users').doc(email).get();

    if (userDoc.exists) {
        return {
            success: true,
            message: "User already exists"
        };
    }

    // Create new user document with email as document ID
    await admin.firestore().collection('users').doc(email).set({
        email: email,
        name: name || "",
        role: role || "parent",
        emailVerified: false,
        createdAt: admin.firestore.FieldValue.serverTimestamp(),
        userId: userId || ""
    });

    logger.info("New user document created for:", email);

    return {
        success: true,
        message: "User document created successfully"
    };
});

// ==================== FUNCTION 2: SEND VERIFICATION EMAIL ====================
exports.sendVerificationEmail = onCall(async (request) => {
    logger.info("Received request data:", request.data);

    const email = request.data.email;

    if (!email) {
        logger.error("No email provided");
        throw new Error("No email provided");
    }

    // Generate unique token
    const token = generateToken();
    const expiresAt = Date.now() + 2 * 60 * 1000; // 2 minutes from now
    const expiresAtReadable = formatReadableDate(expiresAt);

    // Store token in Firestore with expiration
    const tokenData = {
        email: email,
        token: token,
        expiresAtReadable: expiresAtReadable,
        expiresAtTimestamp: expiresAt,
        used: false,
        createdAt: admin.firestore.FieldValue.serverTimestamp()
    };

    await admin.firestore().collection('verificationTokens').doc(token).set(tokenData);

    // 🔥 CRITICAL CHANGE: Include email in the verification link
    const webLink = `https://manjano-bus.web.app/verify.html?token=${token}&email=${encodeURIComponent(email)}`;

    logger.info("Generated verification link for:", email);
    logger.info("Token expires at:", expiresAtReadable);

    const logoUrl = "https://firebasestorage.googleapis.com/v0/b/manjano-bus.firebasestorage.app/o/App%20Assets%2Fic_logo.png?alt=media&token=96d7f4e2-2ac7-4731-887d-7d6bce42f552";

    const mailOptions = {
        from: "Manjano Bus App <reneegithinji@yahoo.com>",
        to: email,
        subject: "Verify your email - Manjano Bus App",
        html: `
            <div style="text-align: center; font-family: Arial, sans-serif;">
                <h2>Verify Your Email Address</h2>
                <p style="color: #666; margin-bottom: 20px;">This link will expire in <strong style="color: #800080;">2 minutes</strong></p>
                <a href="${webLink}" style="
                    background-color:#800080;
                    color:white;
                    padding:12px 24px;
                    text-decoration:none;
                    border-radius:5px;
                    display:inline-block;
                    font-size:16px;
                    margin:20px 0;
                ">Click to Verify Email</a>
                <p style="color: #999; font-size: 12px; margin-top: 20px;">
                    If you didn't request this, please ignore this email.
                </p>
                <div style="margin-top: 40px;">
                    <img src="${logoUrl}"
                         alt="Manjano Bus App"
                         style="width: 80px; height: auto; display: block; margin: 0 auto;"
                         onerror="this.style.display='none'">
                    <p style="color: #800080; font-size: 14px; margin-top: 8px;">Manjano Bus App</p>
                </div>
            </div>
        `,
        text: `
Verify Your Email Address - Manjano Bus App

This link will expire in 2 minutes.

Click the link below to verify your email:

${webLink}

If you didn't request this, please ignore this email.
        `
    };

    try {
        await transporter.sendMail(mailOptions);
        logger.info("Email sent successfully to:", email);
        return { success: true, message: "Verification email sent. Valid for 2 minutes." };
    } catch (err) {
        logger.error("Email error:", err);
        throw new Error("Failed to send email: " + err.message);
    }
});

// ==================== FUNCTION 3: VERIFY EMAIL TOKEN ====================
exports.verifyEmail = onCall(async (request) => {
    const { token, email } = request.data;

    logger.info("=== VERIFY EMAIL CALLED ===");
    logger.info("Token received:", token);
    logger.info("Email received from URL:", email);

    if (!token && !email) {
        logger.error("No token or email provided");
        throw new Error("No verification token or email provided");
    }

    let userEmail = email;

    // 🔥 If email was not in the URL, try to get it from the token document
    if (!userEmail && token) {
        logger.info("Email not in URL, checking token document...");
        const tokenDoc = await admin.firestore().collection('verificationTokens').doc(token).get();
        if (tokenDoc.exists) {
            userEmail = tokenDoc.data().email;
            logger.info("Email found from token document:", userEmail);
        }
    }

    if (!userEmail) {
        logger.error("Could not determine email");
        return {
            success: false,
            error: "no_email",
            message: "Unable to verify. Please request a new link."
        };
    }

    // 🔥 STEP 1: ALWAYS check if user already exists in parents collection FIRST
    // This runs regardless of whether token exists or not
    logger.info("Checking if email exists in parents collection...");
    const parentsRef = admin.firestore().collection('parents');
    const parentQuery = await parentsRef.where('email', '==', userEmail).get();

    logger.info("Parents query result size:", parentQuery.size);

    if (!parentQuery.empty) {
        // User already has an active account
        logger.info("✅ Email found in parents collection. Returning account_active");
        return {
            success: false,
            error: "account_active",
            email: userEmail,
            message: "Your account is already active. Taking you to sign in..."
        };
    }

    logger.info("Email NOT found in parents collection. User is new.");

    // 🔥 STEP 2: User is new - now validate the token
    if (!token) {
        logger.error("No token provided for new user");
        return {
            success: false,
            error: "invalid",
            message: "Invalid verification link. Please request a new one."
        };
    }

    const tokenDoc = await admin.firestore().collection('verificationTokens').doc(token).get();

    if (!tokenDoc.exists) {
        logger.error("Token does not exist in Firestore");
        return {
            success: false,
            error: "invalid",
            message: "Invalid verification link. Please request a new one."
        };
    }

    const tokenData = tokenDoc.data();

    // Verify the email in token matches the email from URL
    if (tokenData.email !== userEmail) {
        logger.error("Email mismatch between token and URL");
        return {
            success: false,
            error: "mismatch",
            message: "Verification link does not match email. Please request a new one."
        };
    }

    // Check if expired
    const now = Date.now();
    const expiryTimestamp = tokenData.expiresAtTimestamp;

    if (now > expiryTimestamp) {
        logger.info("Token expired. Returning expired error");
        await admin.firestore().collection('verificationTokens').doc(token).delete();
        return {
            success: false,
            error: "expired",
            message: "Link expired, please go back to the app and request for another link."
        };
    }

    // Check if already used
    if (tokenData.used) {
        logger.info("Token already used. Returning used error");
        return {
            success: false,
            error: "used",
            message: "This link has already been used. Please request a new verification email."
        };
    }

    // Mark token as used
    logger.info("Token valid. Marking as used and completing verification...");
    await admin.firestore().collection('verificationTokens').doc(token).update({
        used: true,
        verifiedAt: admin.firestore.FieldValue.serverTimestamp()
    });

    // Update user's email verification status in users collection
    const usersRef = admin.firestore().collection('users');
    const userDoc = await usersRef.doc(userEmail).get();

    if (userDoc.exists) {
        await usersRef.doc(userEmail).update({
            emailVerified: true,
            verifiedAt: admin.firestore.FieldValue.serverTimestamp()
        });
    }

    logger.info("Verification completed successfully");
    return {
        success: true,
        email: userEmail,
        message: "Email verified successfully!"
    };
});

// ==================== FUNCTION 4: CLEAN UP EXPIRED TOKENS ====================
const { onSchedule } = require("firebase-functions/v2/scheduler");

exports.cleanupExpiredTokens = onSchedule("every 60 minutes", async (event) => {
    const now = Date.now();

    const expiredTokens = await admin.firestore()
        .collection('verificationTokens')
        .where('expiresAtTimestamp', '<', now)
        .get();

    if (expiredTokens.empty) {
        logger.info("No expired tokens to clean up");
        return;
    }

    const batch = admin.firestore().batch();
    expiredTokens.docs.forEach(doc => {
        batch.delete(doc.ref);
    });

    await batch.commit();
    logger.info(`✅ Cleaned up ${expiredTokens.size} expired tokens`);
});

// ==================== FUNCTION 5: TEST REPORT - RUN NOW ====================
exports.testReportNow = onCall(async (request) => {
    const adminEmail = "reneegithinji@yahoo.com";

    try {
        const parentsSnapshot = await admin.firestore()
            .collection('parents')
            .where('emailVerified', '==', true)
            .get();

        const driversSnapshot = await admin.firestore()
            .collection('drivers')
            .where('emailVerified', '==', true)
            .get();

        let parentsList = "";
        parentsSnapshot.forEach(doc => {
            const data = doc.data();
            parentsList += `${data.parentName || "Unknown"} | ${data.email || "No email"} | ${data.mobileNumber || "No phone"} | ${data.schoolName || "No school"}\n`;
        });

        let driversList = "";
        driversSnapshot.forEach(doc => {
            const data = doc.data();
            driversList += `${data.name || "Unknown"} | ${data.email || "No email"} | ${data.mobileNumber || "No phone"} | ${data.schoolName || "No school"}\n`;
        });

        await transporter.sendMail({
            from: "Manjano Bus App <reneegithinji@yahoo.com>",
            to: adminEmail,
            subject: `📊 TEST REPORT - Verified Users (${parentsSnapshot.size + driversSnapshot.size} total)`,
            text: `
TEST REPORT - Manjano Bus App
Date: ${new Date().toLocaleDateString()}

✅ VERIFIED PARENTS: ${parentsSnapshot.size}
${parentsList || "None"}

✅ VERIFIED DRIVERS: ${driversSnapshot.size}
${driversList || "None"}

TOTAL VERIFIED USERS: ${parentsSnapshot.size + driversSnapshot.size}
            `
        });

        logger.info(`Test report sent to ${adminEmail}`);

        return {
            success: true,
            parents: parentsSnapshot.size,
            drivers: driversSnapshot.size,
            total: parentsSnapshot.size + driversSnapshot.size,
            message: "Test report sent to your email"
        };

    } catch (error) {
        logger.error("Failed to send test report:", error);
        throw new Error("Failed to send test report: " + error.message);
    }
});