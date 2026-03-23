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
    // This is clickable in ALL email clients
    const webLink = `https://manjano-app.web.app/verify?email=${encodeURIComponent(email)}`;
    const appLink = `manjanoapp://verify?email=${encodeURIComponent(email)}`;

    logger.info("Generated web link:", webLink);

    const mailOptions = {
        from: "Manjano Bus App <reneegithinji@yahoo.com>",
        to: email,
        subject: "Verify your email - Manjano Bus App",
        html: `
            <h3>Welcome to Manjano Bus App</h3>
            <p>Click the button below to verify your email:</p>
            <a href="${webLink}" style="
                background-color:#800080;
                color:white;
                padding:10px 20px;
                text-decoration:none;
                border-radius:5px;
                display:inline-block;
                margin:10px 0;
            ">Click to Verify Email</a>
            <p>If the button doesn't work, copy this link:</p>
            <p><a href="${webLink}">${webLink}</a></p>
            <p style="font-size:12px; color:gray;">This will open the Manjano Bus App to complete verification.</p>
        `,
        text: `
Welcome to Manjano Bus App

Click the link below to verify your email:

${webLink}

This will open the Manjano Bus App to complete verification.
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