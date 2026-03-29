const { onCall } = require("firebase-functions/v2/https");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const { logger } = require("firebase-functions/v2");
const { onDocumentCreated, onDocumentUpdated } = require("firebase-functions/v2/firestore");
const { onObjectFinalized } = require("firebase-functions/v2/storage");
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
    // Add 3 hours for Kenya time (UTC+3)
    const kenyaTimestamp = timestamp + (3 * 60 * 60 * 1000);
    const date = new Date(kenyaTimestamp);
    const hours = date.getUTCHours();
    const minutes = date.getUTCMinutes();
    const ampm = hours >= 12 ? 'PM' : 'AM';
    const hour12 = hours % 12 || 12;
    const minuteStr = minutes < 10 ? '0' + minutes : minutes;

    const day = date.getUTCDate();
    const month = date.toLocaleString('default', { month: 'long', timeZone: 'UTC' });
    const year = date.getUTCFullYear();

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

    // Check if there's an existing token that was already used successfully
    const sanitizedEmail = email.replace(/\./g, '_');
    const existingTokenDoc = await admin.firestore().collection('verificationTokens').doc(sanitizedEmail).get();

    let token = null;
    let expiresAt = null;
    let expiresAtReadable = null;

    // If existing token is NOT used, generate a new token
    // If existing token IS used, keep the old token (do NOT generate new one)
    if (existingTokenDoc.exists && existingTokenDoc.data().used === true) {
        // Already verified - keep existing token, don't create new one
        const existingData = existingTokenDoc.data();
        token = existingData.token;
        expiresAt = existingData.expiresAtTimestamp;
        expiresAtReadable = existingData.expiresAtReadable;
        logger.info("✅ Using existing token for already verified email:", email);
    } else {
        // Generate new token for new or incomplete signups
        token = generateToken();
        expiresAt = Date.now() + 2 * 60 * 1000; // 2 minutes from now
        expiresAtReadable = formatReadableDate(expiresAt);

        const tokenData = {
            email: email,
            token: token,
            expiresAtReadable: expiresAtReadable,
            expiresAtTimestamp: expiresAt,
            used: false,
            createdAt: admin.firestore.FieldValue.serverTimestamp()
        };

        logger.info("Storing new token with document ID (sanitized email):", sanitizedEmail);
        await admin.firestore().collection('verificationTokens').doc(sanitizedEmail).set(tokenData);
    }

    // ALWAYS send the email (whether new token or existing token)
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
    logger.info("userEmail variable after assignment:", userEmail);

    // If email was not in the URL, try to get it from the token document
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

    logger.info("Final userEmail being used for parents check:", userEmail);

    // STEP 1: ALWAYS check if user already exists in parents collection FIRST
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

    // STEP 2: User is new - now validate the token
    if (!token) {
        logger.error("No token provided for new user");
        return {
            success: false,
            error: "invalid",
            message: "Invalid verification link. Please request a new one."
        };
    }

    // Sanitize email to match document ID
    const sanitizedEmail = userEmail.replace(/\./g, '_');
    logger.info("Looking up token document with sanitized email:", sanitizedEmail);
    const tokenDoc = await admin.firestore().collection('verificationTokens').doc(sanitizedEmail).get();
    logger.info("Token document exists?", tokenDoc.exists);

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
        const sanitizedEmailForDelete = userEmail.replace(/\./g, '_');
        await admin.firestore().collection('verificationTokens').doc(sanitizedEmailForDelete).delete();
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
    const sanitizedEmailForUpdate = userEmail.replace(/\./g, '_');
    await admin.firestore().collection('verificationTokens').doc(sanitizedEmailForUpdate).update({
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

// ==================== FUNCTION 6: ADD ACTIVE FIELD TO ALL PARENTS ====================
exports.addActiveFieldToAllParents = onCall(async (request) => {
    const parentsRef = admin.firestore().collection('parents');
    const snapshot = await parentsRef.get();

    let updatedCount = 0;
    const batch = admin.firestore().batch();

    snapshot.forEach(doc => {
        // Only add active field if it doesn't exist
        if (!doc.data().hasOwnProperty('active')) {
            batch.update(doc.ref, { active: true });
            updatedCount++;
        }
    });

    await batch.commit();
    logger.info(`✅ Added active:true to ${updatedCount} parent documents`);
    return { success: true, updatedCount: updatedCount };
});

// ==================== FUNCTION: ADD ACTIVE FIELD TO ALL DRIVERS ====================
exports.addActiveFieldToAllDrivers = onCall(async (request) => {
    const driversRef = admin.firestore().collection('drivers');
    const snapshot = await driversRef.get();

    let updatedCount = 0;
    const batch = admin.firestore().batch();

    snapshot.forEach(doc => {
        if (!doc.data().hasOwnProperty('active')) {
            batch.update(doc.ref, { active: true });
            updatedCount++;
        }
    });

    await batch.commit();
    logger.info(`✅ Added active:true to ${updatedCount} driver documents`);
    return { success: true, updatedCount: updatedCount };
});

// ==================== FUNCTION 7: SYNC PARENT ACTIVE STATUS TO REALTIME DATABASE (v2) ====================
exports.syncParentActiveStatus = onDocumentUpdated("parents/{parentId}", async (event) => {
    const beforeData = event.data.before.data();
    const afterData = event.data.after.data();

    const beforeActive = beforeData.active;
    const afterActive = afterData.active;

    // Only proceed if active status actually changed
    if (beforeActive === afterActive) {
        logger.info('Active status unchanged, skipping');
        return null;
    }

    const parentName = afterData.parentName || '';
    const parentPhone = event.params.parentId;

    logger.info(`Parent ${parentName} (${parentPhone}) active status changed: ${beforeActive} → ${afterActive}`);

    // Helper function to get image URL from Firebase Storage
   async function getChildImageUrl(childKey) {
       try {
           const bucket = admin.storage().bucket();

           // Pattern 1: With underscores (original behavior)
           const withUnderscores = childKey;

           // Pattern 2: Without underscores (remove underscores)
           const withoutUnderscores = childKey.replace(/_/g, '');

           const extensions = ['png', 'jpg'];

           for (const ext of extensions) {
               // Try with underscores
               const pathWithUnderscores = `Children Images/${withUnderscores}.${ext}`;
               const fileWithUnderscores = bucket.file(pathWithUnderscores);
               const [existsWithUnderscores] = await fileWithUnderscores.exists();

               if (existsWithUnderscores) {
                   const encodedFileName = encodeURIComponent(`${withUnderscores}.${ext}`);
                   const url = `https://firebasestorage.googleapis.com/v0/b/manjano-bus.firebasestorage.app/o/Children%20Images%2F${encodedFileName}?alt=media`;
                   logger.info(`✅ Found image (with underscores) for ${childKey} as ${withUnderscores}.${ext}`);
                   return url;
               }

               // Try without underscores
               const pathWithoutUnderscores = `Children Images/${withoutUnderscores}.${ext}`;
               const fileWithoutUnderscores = bucket.file(pathWithoutUnderscores);
               const [existsWithoutUnderscores] = await fileWithoutUnderscores.exists();

               if (existsWithoutUnderscores) {
                   const encodedFileName = encodeURIComponent(`${withoutUnderscores}.${ext}`);
                   const url = `https://firebasestorage.googleapis.com/v0/b/manjano-bus.firebasestorage.app/o/Children%20Images%2F${encodedFileName}?alt=media`;
                   logger.info(`✅ Found image (without underscores) for ${childKey} as ${withoutUnderscores}.${ext}`);
                   return url;
               }
           }

           logger.info(`⚠️ No image found in Storage for ${childKey} (tried with and without underscores, .png and .jpg)`);
           return '';
       } catch (error) {
           logger.info(`Error checking Storage for ${childKey}: ${error.message}`);
           return '';
       }
   }
    // Collect children names from parent document
    const childrenNames = [];

    // Case 1: Single child (stored as 'childName')
    const singleChild = afterData.childName;
    if (singleChild && singleChild.trim() !== '') {
        childrenNames.push(singleChild.trim());
    }

    // Case 2: Multiple children (stored as 'childName1', 'childName2', etc.)
    for (let i = 1; i <= 10; i++) {
        const childName = afterData[`childName${i}`];
        if (childName && childName.trim() !== '') {
            childrenNames.push(childName.trim());
        }
    }

    if (childrenNames.length === 0) {
        logger.info('No children found for this parent in parent document');
        return null;
    }

    logger.info(`Found ${childrenNames.length} children: ${childrenNames.join(', ')}`);

    const db = admin.database();
    const firestore = admin.firestore();

    // Process each child
    for (const childName of childrenNames) {
        const childKey = childName.toLowerCase().replace(/[^a-z0-9]/g, '_');

        if (afterActive === true) {
            logger.info(`Reactivating child: ${childName} (${childKey})`);

            // Get child data from children collection
            const childrenCollection = firestore.collection('children');
            const querySnapshot = await childrenCollection
                .where('childName', '==', childName)
                .where('parentName', '==', parentName)
                .limit(1)
                .get();

            if (!querySnapshot.empty) {
                const childDoc = querySnapshot.docs[0];
                const childData = childDoc.data();

                // Try to get image URL from Storage
                const imageUrl = await getChildImageUrl(childKey);
                const finalPhotoUrl = imageUrl || '';

                const rtdbData = {
                    active: true,
                    childId: childKey,
                    displayName: childName,
                    eta: childData.eta || 'Arriving in 5 minutes',
                    parentName: parentName,
                    photoUrl: finalPhotoUrl,
                    status: childData.status || 'On Route',
                    pickUpAddress: childData.pickUpAddress || '',
                    pickUpPlaceId: childData.pickUpPlaceId || '',
                    pickUpLat: childData.pickUpLat || 0,
                    pickUpLng: childData.pickUpLng || 0,
                    dropOffAddress: childData.dropOffAddress || '',
                    dropOffPlaceId: childData.dropOffPlaceId || '',
                    dropOffLat: childData.dropOffLat || 0,
                    dropOffLng: childData.dropOffLng || 0
                };

                await db.ref(`students/${childKey}`).set(rtdbData);

                const parentKey = parentName.toLowerCase().replace(/[^a-z0-9]/g, '_');
                await db.ref(`parents/${parentKey}/children/${childKey}`).set(rtdbData);

                logger.info(`✅ Child ${childName} reactivated with image: ${finalPhotoUrl ? 'YES' : 'NO'}`);
            } else {
                logger.info(`⚠️ No Firestore data found for child ${childName}`);
            }

        } else {
            logger.info(`Deactivating child: ${childName} (${childKey})`);

            await db.ref(`students/${childKey}`).remove();

            const parentKey = parentName.toLowerCase().replace(/[^a-z0-9]/g, '_');
            await db.ref(`parents/${parentKey}/children/${childKey}`).remove();

            logger.info(`✅ Child ${childName} removed`);
        }
    }

    const parentKey = parentName.toLowerCase().replace(/[^a-z0-9]/g, '_');
    await db.ref(`parents/${parentKey}/active`).set(afterActive);

    logger.info(`✅ Parent ${parentName} sync complete`);
    return null;
});

// ==================== FUNCTION 8: SYNC NEW PARENT TO REALTIME DATABASE (v2) ====================
exports.syncNewParent = onDocumentCreated("parents/{parentId}", async (event) => {
    const data = event.data.data();
    const parentName = data.parentName || '';
    const parentPhone = event.params.parentId;

    logger.info(`New parent created: ${parentName} (${parentPhone})`);

    if (data.active !== false) {
        // Collect children names from parent document
        const childrenNames = [];

        // Case 1: Single child
        const singleChild = data.childName;
        if (singleChild && singleChild.trim() !== '') {
            childrenNames.push(singleChild.trim());
        }

        // Case 2: Multiple children
        for (let i = 1; i <= 10; i++) {
            const childName = data[`childName${i}`];
            if (childName && childName.trim() !== '') {
                childrenNames.push(childName.trim());
            }
        }

        if (childrenNames.length === 0) {
            logger.info('No children found for new parent in parent document');
            return null;
        }

        const db = admin.database();
        const firestore = admin.firestore();
        const parentKey = parentName.toLowerCase().replace(/[^a-z0-9]/g, '_');

        for (const childName of childrenNames) {
            const childKey = childName.toLowerCase().replace(/[^a-z0-9]/g, '_');

            // Get child data from children collection
            const childDoc = await firestore.collection('children')
                .where('childName', '==', childName)
                .where('parentName', '==', parentName)
                .limit(1)
                .get();

            const childData = childDoc.empty ? {} : childDoc.docs[0].data();

            const rtdbData = {
                active: true,
                childId: childKey,
                displayName: childName,
                eta: 'Arriving in 5 minutes',
                parentName: parentName,
                photoUrl: '',
                status: 'On Route',
                pickUpAddress: childData.pickUpAddress || '',
                pickUpPlaceId: childData.pickUpPlaceId || '',
                pickUpLat: childData.pickUpLat || 0,
                pickUpLng: childData.pickUpLng || 0,
                dropOffAddress: childData.dropOffAddress || '',
                dropOffPlaceId: childData.dropOffPlaceId || '',
                dropOffLat: childData.dropOffLat || 0,
                dropOffLng: childData.dropOffLng || 0
            };

            await db.ref(`students/${childKey}`).set(rtdbData);
            await db.ref(`parents/${parentKey}/children/${childKey}`).set(rtdbData);

            logger.info(`✅ Child ${childName} synced`);
        }

        await db.ref(`parents/${parentKey}/active`).set(true);
        logger.info(`✅ New parent ${parentName} synced`);
    } else {
        logger.info(`New parent ${parentName} is inactive, skipping sync`);
    }

    return null;
});

// ==================== FUNCTION 9: MIGRATE EXISTING CHILDREN - ADD PHOTOURL FIELD ====================
exports.migrateChildrenAddPhotoUrl = onCall(async (request) => {
    const childrenRef = admin.firestore().collection('children');
    const snapshot = await childrenRef.get();

    let updatedCount = 0;
    const batch = admin.firestore().batch();

    snapshot.forEach(doc => {
        const data = doc.data();
        if (!data.hasOwnProperty('photoUrl')) {
            batch.update(doc.ref, { photoUrl: '' });
            updatedCount++;
        }
    });

    await batch.commit();
    logger.info(`✅ Added photoUrl field to ${updatedCount} children`);
    return { success: true, updatedCount: updatedCount };
});

// ==================== FUNCTION 10: AUTO UPDATE CHILD IMAGE WHEN UPLOADED TO STORAGE ====================
exports.onChildImageUploaded = onObjectFinalized({
    bucket: "manjano-bus.firebasestorage.app",
    object: "Children Images/*.{png,jpg}"
}, async (event) => {
    const filePath = event.data.name;
    const fileName = filePath.split('/').pop();

    // Extract child key from filename (remove extension, keep as-is without converting underscores)
    let childKey = fileName.replace(/\.(png|jpg)$/i, '').toLowerCase();
    // Also create a version without underscores for matching
    const childKeyNoUnderscores = childKey.replace(/_/g, '');

    logger.info(`📸 New image uploaded: ${filePath}`);
    logger.info(`Child key derived (with underscores): ${childKey}`);
    logger.info(`Child key derived (without underscores): ${childKeyNoUnderscores}`);

    // Construct the public URL
    const encodedFileName = encodeURIComponent(fileName);
    const imageUrl = `https://firebasestorage.googleapis.com/v0/b/manjano-bus.firebasestorage.app/o/Children%20Images%2F${encodedFileName}?alt=media`;

    const db = admin.database();

    // Try to find the child in Realtime Database using both key patterns
    let snapshot = await db.ref(`students/${childKey}`).once('value');
    let actualChildKey = childKey;

    if (!snapshot.exists()) {
        // Try without underscores
        snapshot = await db.ref(`students/${childKeyNoUnderscores}`).once('value');
        actualChildKey = childKeyNoUnderscores;
    }

    if (snapshot.exists()) {
        await db.ref(`students/${actualChildKey}/photoUrl`).set(imageUrl);
        logger.info(`✅ Updated photoUrl for student ${actualChildKey} in Realtime Database`);

        // Also update the parent's children node if it exists
        const parentName = snapshot.child('parentName').val();
        if (parentName) {
            const parentKey = parentName.toLowerCase().replace(/[^a-z0-9]/g, '_');
            const parentChildRef = db.ref(`parents/${parentKey}/children/${actualChildKey}/photoUrl`);
            await parentChildRef.set(imageUrl);
            logger.info(`✅ Updated photoUrl in parent node for ${actualChildKey}`);
        }
    } else {
        logger.info(`⚠️ Child ${childKey} (or ${childKeyNoUnderscores}) not found in Realtime Database yet. Image will be used when child is reactivated.`);
    }

    return null;
});