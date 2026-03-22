const { onCall } = require("firebase-functions/v2/https");
const { logger } = require("firebase-functions/v2");
const admin = require('firebase-admin');
const nodemailer = require("nodemailer");

// Initialize Firebase Admin
admin.initializeApp();

// Configure email transporter
const transporter = nodemailer.createTransport({
    service: "yahoo",
    auth: {
        user: "reneegithinji@yahoo.com",
        pass: "hpnzhrwgtuiuoxca"
    }
});

// The main function - using v2 syntax
exports.sendVerificationEmail = onCall(async (request) => {
    // In v2, the data is in request.data
    logger.info("Received request data:", request.data);

    // Get the email from request.data
    const email = request.data.email;

    logger.info("Extracted email:", email);

    if (!email) {
        logger.error("No email provided");
        throw new Error("No email provided");
    }

    // Generate verification link
    const link = `https://manjano-app.web.app/verify?email=${encodeURIComponent(email)}`;

    const mailOptions = {
        from: "Manjano Bus App <reneegithinji@yahoo.com>",
        to: email,
        subject: "Verify your email - Manjano Bus App",
        html: `
            <h3>Welcome to Manjano Bus App</h3>
            <p>Click the button below to verify your email:</p>
            <a href="${link}" style="
                background-color:#800080;
                color:white;
                padding:10px 20px;
                text-decoration:none;
                border-radius:5px;
                display:inline-block;
            ">Verify Email</a>
            <p>If the button doesn't work, copy this link:</p>
            <p>${link}</p>
        `
    };

    try {
        const info = await transporter.sendMail(mailOptions);
        logger.info("Email sent successfully to:", email);
        return { success: true, messageId: info.messageId };
    } catch (err) {
        logger.error("Email error:", err);
        throw new Error("Failed to send email: " + err.message);
    }
});