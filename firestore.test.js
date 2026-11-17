const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
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

test("Unauthenticated users cannot read or write any workspace collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const past = new Date(Date.now() - 1000);

  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("technicians").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("orders").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("technicians")
      .doc("tech_1")
      .set({
        userId: ALICE_UID,
        fullName: "Carlos Méndez",
        documentNumber: "1110482910",
        phone: "3165275176",
        barrio: "El Jardín",
        specialties: ["ELECTRICISTA"],
        departureTime: "07:30",
        workSchedule: "07:30 - 17:30 L-S",
        latitude: 4.44,
        longitude: -75.21,
        status: "DISPONIBLE",
        createdAt: past,
        updatedAt: past,
      })
  );
});

test("UserProfile create and partial update succeed for owner, reject ghost fields and cross-user access", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const past = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "César Díaz",
      email: "cesar@ibaguetqm.co",
      phone: "3165275176",
      roleLabel: "ADMIN",
      preferredZone: "Comuna 5 - El Jardín",
      themeMode: "DARK",
      twoFactorEnabled: true,
      twoFactorPinHash: "",
      pushAssignments: true,
      pushPayments: true,
      pushRouteAlerts: true,
      webhookEnabled: false,
      webhookUrl: "",
      webhookApiKey: "",
      createdAt: past,
      updatedAt: past,
    })
  );

  // Partial update allowed
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      themeMode: "LIGHT",
      webhookEnabled: true,
      webhookUrl: "https://hooks.ibaguetqm.co/orders",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject ghost field
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      superAdmin: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Cross-user isolation
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      displayName: "Hijacked",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Technician create, list, update, and validation guards enforce schema parity", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const past = new Date(Date.now() - 5000);

  const techRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("technicians")
    .doc("tech_1");

  await assertSucceeds(
    techRef.set({
      userId: ALICE_UID,
      fullName: "Andrés Felipe Rojas",
      documentNumber: "1110523980",
      phone: "3014203319",
      email: "andres@ibaguetqm.co",
      address: "Cra 5 # 42-18",
      barrio: "Piedra Pintada",
      specialties: ["ELECTRICISTA", "AIRE_ACONDICIONADO"],
      departureTime: "07:00",
      workSchedule: "07:00 - 17:00 L-S",
      knowledgeNotes: "Certificación RETIE, minisplits inverter",
      latitude: 4.4412,
      longitude: -75.2055,
      status: "DISPONIBLE",
      rating: 4.9,
      completedCount: 12,
      createdAt: past,
      updatedAt: past,
    })
  );

  // List succeeds for owner, fails for Bob
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("technicians").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("technicians").get()
  );

  // Partial status update succeeds
  await assertSucceeds(
    techRef.update({
      status: "EN_RUTA",
      latitude: 4.445,
      longitude: -75.198,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject @DocumentId `id` inside body
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("technicians")
      .doc("tech_2")
      .set({
        id: "tech_2",
        userId: ALICE_UID,
        fullName: "Luis Gómez",
        documentNumber: "93380123",
        phone: "3165275176",
        barrio: "Centro",
        specialties: ["PLOMERO"],
        departureTime: "08:00",
        workSchedule: "08:00 - 18:00",
        latitude: 4.44,
        longitude: -75.24,
        status: "DISPONIBLE",
        createdAt: past,
        updatedAt: past,
      })
  );

  // Reject future tampered timestamp
  await assertFails(
    techRef.update({
      status: "DISPONIBLE",
      updatedAt: new Date(Date.now() + 60000),
    })
  );
});

test("ServiceOrder lifecycle, payment update, and terminal state locking", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const past = new Date(Date.now() - 5000);

  const orderRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("orders")
    .doc("order_1");

  await assertSucceeds(
    orderRef.set({
      userId: ALICE_UID,
      orderCode: "TQM-1001",
      clientName: "María Fernanda Ospina",
      clientPhone: "3124567890",
      address: "Calle 60 # 12-40",
      barrio: "La Floresta",
      latitude: 4.439,
      longitude: -75.194,
      specialty: "NEVERAS",
      priority: "URGENTE",
      status: "PENDIENTE",
      assignedTechId: "",
      assignedTechName: "",
      scheduledTime: "10:00",
      estimatedCostCop: 140000,
      paymentMethod: "NEQUI",
      paymentReference: "",
      paymentStatus: "PENDIENTE",
      description: "Nevera no enfría en la parte inferior",
      technicianNotes: "",
      clientRating: 0,
      createdAt: past,
      updatedAt: past,
    })
  );

  // Assign & complete order
  await assertSucceeds(
    orderRef.update({
      status: "COMPLETADA",
      assignedTechId: "tech_1",
      assignedTechName: "Andrés Felipe Rojas",
      technicianNotes: "Cambio de termostato y carga de gas R600a",
      updatedAt: new Date(Date.now() - 2000),
    })
  );

  // Once COMPLETADA, cannot revert status or mutate clientName
  await assertFails(
    orderRef.update({
      status: "PENDIENTE",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Once COMPLETADA, client payment verification & rating succeed
  await assertSucceeds(
    orderRef.update({
      paymentMethod: "BANCOLOMBIA",
      paymentReference: "BC-992817",
      paymentStatus: "PAGADO",
      clientRating: 5,
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Notifications, SupportTickets, and CloudBackups enforce ownership and schema", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const past = new Date(Date.now() - 3000);

  const notifRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("notifications")
    .doc("notif_1");
  await assertSucceeds(
    notifRef.set({
      userId: ALICE_UID,
      title: "Servicio asignado",
      message: "Orden TQM-1001 asignada a Andrés Felipe Rojas",
      notifType: "ASIGNACION",
      orderId: "order_1",
      readFlag: false,
      createdAt: past,
      updatedAt: past,
    })
  );
  await assertSucceeds(
    notifRef.update({
      readFlag: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  const ticketRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("supportTickets")
    .doc("ticket_1");
  await assertSucceeds(
    ticketRef.set({
      userId: ALICE_UID,
      subject: "Garantía compresor aire acondicionado",
      category: "AIRE_ACONDICIONADO",
      priority: "ALTA",
      description: "Solicito revisión de garantía sobre instalación reciente.",
      status: "ABIERTO",
      createdAt: past,
      updatedAt: past,
    })
  );

  const backupRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("backups")
    .doc("backup_1");
  await assertSucceeds(
    backupRef.set({
      userId: ALICE_UID,
      label: "Respaldo Automático Diario",
      techCount: 4,
      orderCount: 10,
      totalRevenueCop: 1250000,
      checksum: "sha256-9a8f12c4",
      createdAt: past,
      updatedAt: past,
    })
  );

  // Backups are immutable
  await assertFails(
    backupRef.update({
      orderCount: 99,
      updatedAt: new Date(Date.now() - 100),
    })
  );

  // Cross-user denied
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("backups").get());
});
