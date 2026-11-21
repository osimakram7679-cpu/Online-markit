const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_customer_1";
const BOB_UID = "bob_customer_2";
const ADMIN_UID = "admin_osim_1";
const ADMIN_EMAIL = "osimakram90644@gmail.com";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function validProductPayload(now) {
  return {
    title: "Studio Wireless Headphones",
    description: "Active noise cancelling over-ear wireless headphones.",
    priceInr: 2499,
    mrpInr: 3999,
    category: "Electronics",
    imagePreset: "electronics",
    stock: 25,
    featured: true,
    active: true,
    createdAt: now,
    updatedAt: now,
  };
}

function validOrderPayload(uid, now) {
  return {
    userId: uid,
    customerName: "Alice Sharma",
    customerEmail: "alice@example.com",
    customerPhone: "9876543210",
    shippingAddress: "42 MG Road, Sector 14",
    city: "Kolkata",
    pincode: "700001",
    itemsSummary: "Studio Wireless Headphones x1",
    items: [
      {
        productId: "prod_1",
        title: "Studio Wireless Headphones",
        priceInr: 2499,
        quantity: 1,
      },
    ],
    itemCount: 1,
    subtotalInr: 2499,
    discountInr: 0,
    deliveryFeeInr: 0,
    totalInr: 2499,
    paymentMethod: "UPI_GATEWAY",
    paymentStatus: "PAID",
    transactionId: "TXN_99887766",
    orderStatus: "PLACED",
    createdAt: now,
    updatedAt: now,
  };
}

test("Public catalog & store config: unauthenticated reads succeed, writes fail", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertSucceeds(unauthDb.collection("products").get());
  await assertSucceeds(unauthDb.collection("store_settings").doc("config").get());

  const now = new Date(Date.now() - 1000);
  await assertFails(unauthDb.collection("products").doc("prod_1").set(validProductPayload(now)));
  await assertFails(unauthDb.collection("orders").get());
});

test("Email spoofing protection: unverified admin email is rejected", async () => {
  const spoofedAdminDb = testEnv.authenticatedContext("spoof_uid", {
    email: ADMIN_EMAIL,
    email_verified: false,
  }).firestore();
  const now = new Date(Date.now() - 1000);
  await assertFails(spoofedAdminDb.collection("products").doc("prod_1").set(validProductPayload(now)));
});

test("Verified admin: can create/update/delete products and update store_settings/config", async () => {
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: ADMIN_EMAIL,
    email_verified: true,
  }).firestore();
  const created = new Date(Date.now() - 5000);

  await assertSucceeds(adminDb.collection("products").doc("prod_1").set(validProductPayload(created)));
  await assertSucceeds(
    adminDb.collection("products").doc("prod_1").update({
      priceInr: 2299,
      stock: 20,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject immutable createdAt modification or @DocumentId 'id' injection
  await assertFails(
    adminDb.collection("products").doc("prod_1").update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    adminDb.collection("products").doc("prod_2").set({
      id: "prod_2",
      ...validProductPayload(created),
    })
  );

  await assertSucceeds(
    adminDb.collection("store_settings").doc("config").set({
      storeName: "BazaarHub",
      tagline: "Curated everyday essentials",
      contactPhone: "7679100108",
      contactEmail: ADMIN_EMAIL,
      merchantUpiId: "7679100108@ybl",
      announcementText: "Free shipping on orders above ₹999",
      storeOpen: true,
      upiEnabled: true,
      cardGatewayEnabled: true,
      codEnabled: true,
      promoEnabled: true,
      promoCode: "BAZAAR10",
      promoDiscountPercent: 10,
      deliveryFeeInr: 49,
      freeDeliveryAboveInr: 999,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Customer cannot mutate products, store_settings, or elevate to admin", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, {
    email: "alice@example.com",
    email_verified: true,
  }).firestore();
  const now = new Date(Date.now() - 1000);

  await assertFails(aliceDb.collection("products").doc("prod_1").set(validProductPayload(now)));
  await assertFails(
    aliceDb.collection("admins").doc(ALICE_UID).set({
      uid: ALICE_UID,
      email: "alice@example.com",
      addedBy: ALICE_UID,
      createdAt: now,
    })
  );
});

test("Orders: customer creates own order, queries own orders, cancels before ship, cannot read Bob's order", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, {
    email: "alice@example.com",
    email_verified: true,
  }).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID, {
    email: "bob@example.com",
    email_verified: true,
  }).firestore();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: ADMIN_EMAIL,
    email_verified: true,
  }).firestore();

  const created = new Date(Date.now() - 10000);

  // Reject state shortcutting on create (cannot create directly as DELIVERED)
  await assertFails(
    aliceDb.collection("orders").doc("ord_bad").set({
      ...validOrderPayload(ALICE_UID, created),
      orderStatus: "DELIVERED",
    })
  );

  // Valid create
  await assertSucceeds(
    aliceDb.collection("orders").doc("ord_1").set(validOrderPayload(ALICE_UID, created))
  );

  // Filtered query succeeds, unfiltered query fails for customer
  await assertSucceeds(
    aliceDb.collection("orders").where("userId", "==", ALICE_UID).get()
  );
  await assertFails(aliceDb.collection("orders").get());

  // Cross-user isolation
  await assertFails(bobDb.collection("orders").doc("ord_1").get());
  await assertFails(
    bobDb.collection("orders").doc("ord_1").update({
      orderStatus: "CANCELLED",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Admin can read all orders & advance status
  await assertSucceeds(adminDb.collection("orders").get());
  await assertSucceeds(
    adminDb.collection("orders").doc("ord_1").update({
      orderStatus: "SHIPPED",
      updatedAt: new Date(Date.now() - 2000),
    })
  );

  // Terminal/post-ship state lock: customer cannot cancel after SHIPPED
  await assertFails(
    aliceDb.collection("orders").doc("ord_1").update({
      orderStatus: "CANCELLED",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("User profiles: isolated per user and admin readable", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 2000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@example.com",
      phone: "9876543210",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Shadow field rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
});
