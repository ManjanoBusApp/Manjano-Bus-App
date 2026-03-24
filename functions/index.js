const { onCall } = require("firebase-functions/v2/https");
const { logger } = require("firebase-functions/v2");
const admin = require('firebase-admin');
const nodemailer = require("nodemailer");

admin.initializeApp();

const transporter = nodemailer.createTransport({
    service: "yahoo",
    auth: {
        user: "reneegithinji@yahoo.com",
        pass: "hpnzhrwgtuiuoxca"
    }
});

exports.sendVerificationEmail = onCall(async (request) => {
    logger.info("Received request data:", request.data);

    const email = request.data.email;

    if (!email) {
        logger.error("No email provided");
        throw new Error("No email provided");
    }

    // Use a normal https:// link that will redirect to the app
    const webLink = `https://manjano-app.web.app/verify?email=${encodeURIComponent(email)}`;

    logger.info("Generated web link:", webLink);

    // Try alternative logo URLs - use the public Firebase Storage URL without token
    // Option 1: Direct download URL (no token, public)
    const logoUrl = "https://firebasestorage.googleapis.com/v0/b/manjano-bus.firebasestorage.app/o/App%20Assets%2Fic_logo.png?alt=media&token=96d7f4e2-2ac7-4731-887d-7d6bce42f552";

    // Option 2: If Option 1 doesn't work, you can use a base64 encoded version
    // But let's try this first

    const mailOptions = {
        from: "Manjano Bus App <reneegithinji@yahoo.com>",
        to: email,
        subject: "Verify your email - Manjano Bus App",
        html: `
            <div style="text-align: center; font-family: Arial, sans-serif;">
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
Welcome to Manjano Bus App

Click the link below to verify your email:

${webLink}
        `
    };

    try {
        await transporter.sendMail(mailOptions);
        logger.info("Email sent successfully to:", email);
        return { success: true };
    } catch (err) {
        logger.error("Email error:", err);
        throw new Error("Failed to send email: " + err.message);
    }
});