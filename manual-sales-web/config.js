// ParcelGuard Manual Sales — owner-configurable public information.
// Do not publish unverified bank details, customer data, or expiring Actions artifacts.
// Once supportUrl is configured, order buttons open that verified channel.
// Download URLs are delivered privately *after* the owner verifies payment.
export const STORE = Object.freeze({
  brand: "ParcelGuard",
  supportUrl: null, // Example after verification: "https://line.me/R/ti/p/@YOUR_OFFICIAL_ID"
  supportLabel: "ติดต่อร้านเพื่อสั่งซื้อ",
  androidDownloadUrl: null,
  windowsDownloadUrl: null,
  salesOpen: false, // remains closed until manual sales readiness checklist passes
});
