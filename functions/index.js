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
    const expiresAtReadable = formatReadableDate(expiresAt); // Nice readable format

    // Store token in Firestore with expiration
    const tokenData = {
        email: email,
        token: token,
        expiresAtReadable: expiresAtReadable,
        expiresAtTimestamp: expiresAt, // Keep timestamp for cleanup
        used: false,
        createdAt: admin.firestore.FieldValue.serverTimestamp()
    };

    await admin.firestore().collection('verificationTokens').doc(token).set(tokenData);

    // Create verification link with token
    const webLink = `https://manjano-bus.web.app/verify.html?token=${token}`;

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
                <p style="color: #666; margin-bottom: 20px;">This link will expire in <strong style="color: #800080;">30 minutes</strong></p>
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

This link will expire in 30 minutes.

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
    const { token } = request.data;

    if (!token) {
        throw new Error("No verification token provided");
    }

    // Get token from Firestore
    const tokenDoc = await admin.firestore().collection('verificationTokens').doc(token).get();

    if (!tokenDoc.exists) {
        return {
            success: false,
            error: "invalid",
            message: "Invalid verification link. Please request a new one."
        };
    }

    const tokenData = tokenDoc.data();

    // Check if already used
    if (tokenData.used) {
        return {
            success: false,
            error: "used",
            message: "This link has already been used. Please request a new verification email."
        };
    }

    // Check if expired using timestamp
    const now = Date.now();
    const expiryTimestamp = tokenData.expiresAtTimestamp;

    if (now > expiryTimestamp) {
        await admin.firestore().collection('verificationTokens').doc(token).delete();
        return {
            success: false,
            error: "expired",
            message: "Link expired, please go back to the app and request for another link."
        };
    }

    // Mark token as used
    await admin.firestore().collection('verificationTokens').doc(token).update({
        used: true,
        verifiedAt: admin.firestore.FieldValue.serverTimestamp()
    });

    // Update user's email verification status in users collection
    const usersRef = admin.firestore().collection('users');
    const userDoc = await usersRef.doc(tokenData.email).get();

    if (userDoc.exists) {
        await usersRef.doc(tokenData.email).update({
            emailVerified: true,
            verifiedAt: admin.firestore.FieldValue.serverTimestamp()
        });
    }

    return {
        success: true,
        email: tokenData.email,
        message: "Email verified successfully!"
    };
});

// ==================== FUNCTION 4: CLEAN UP EXPIRED TOKENS ====================
// Runs every hour and deletes tokens that have expired
const { onSchedule } = require("firebase-functions/v2/scheduler");

exports.cleanupExpiredTokens = onSchedule("every 60 minutes", async (event) => {
    const now = Date.now();

    // Get all tokens that have expired using timestamp field
    const expiredTokens = await admin.firestore()
        .collection('verificationTokens')
        .where('expiresAtTimestamp', '<', now)
        .get();

    if (expiredTokens.empty) {
        logger.info("No expired tokens to clean up");
        return;
    }

    // Delete all expired tokens in a batch
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
        // Get verified parents
        const parentsSnapshot = await admin.firestore()
            .collection('parents')
            .where('emailVerified', '==', true)
            .get();

        // Get verified drivers
        const driversSnapshot = await admin.firestore()
            .collection('drivers')
            .where('emailVerified', '==', true)
            .get();

        // Build email content
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

        // Send email
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