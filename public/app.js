/* ParcelGuard pre-launch static website
 * No payments, no tracking pixels, no registration or license issuance.
 * When a privacy-reviewed real demo is ready, set this relative path:
 * const DEMO_VIDEO_URL = "./assets/parcelguard-demo.mp4";
 */
(() => {
  "use strict";
  const DEMO_VIDEO_URL = "";
  const menuButton = document.getElementById("menuToggle");
  const menu = document.getElementById("navLinks");
  const trialDialog = document.getElementById("trialDialog");
  const footerYear = document.getElementById("footerYear");
  const demoVideo = document.getElementById("demoVideo");

  if (footerYear) footerYear.textContent = String(new Date().getFullYear());

  function closeMenu() {
    if (!menu || !menuButton) return;
    menu.classList.remove("is-open");
    menuButton.setAttribute("aria-expanded", "false");
    menuButton.setAttribute("aria-label", "เปิดเมนู");
  }
  if (menuButton && menu) {
    menuButton.addEventListener("click", () => {
      const isOpening = !menu.classList.contains("is-open");
      menu.classList.toggle("is-open", isOpening);
      menuButton.setAttribute("aria-expanded", String(isOpening));
      menuButton.setAttribute("aria-label", isOpening ? "ปิดเมนู" : "เปิดเมนู");
    });
    menu.querySelectorAll("a").forEach((link) =>
      link.addEventListener("click", closeMenu)
    );
    document.addEventListener("keydown", (event) => {
      if (event.key === "Escape") closeMenu();
    });
    document.addEventListener("click", (event) => {
      if (menu.classList.contains("is-open") &&
          !menu.contains(event.target) && !menuButton.contains(event.target)) {
        closeMenu();
      }
    });
  }

  document.querySelectorAll("[data-trial]").forEach((button) => {
    button.addEventListener("click", () => {
      closeMenu();
      if (trialDialog && typeof trialDialog.showModal === "function") {
        trialDialog.showModal();
      } else {
        window.alert("ParcelGuard กำลังเตรียมระบบทดลองใช้ฟรี 14 วัน ยังไม่เปิดรับสมัครหรือชำระเงินจริง");
      }
    });
  });

  if (trialDialog) {
    trialDialog.addEventListener("click", (event) => {
      if (event.target === trialDialog) trialDialog.close();
    });
  }

  // Replace the honest pre-launch placeholder with a real local MP4 only
  // once that file is supplied. No external player and no autoplay.
  if (DEMO_VIDEO_URL && demoVideo) {
    const source = document.createElement("source");
    source.src = DEMO_VIDEO_URL;
    source.type = "video/mp4";
    demoVideo.appendChild(source);
    demoVideo.hidden = false;
    const placeholder = document.querySelector(".demo-play");
    if (placeholder) placeholder.hidden = true;
    const status = document.querySelector(".demo-bottom > span:first-child");
    if (status) status.textContent = "วิดีโอสาธิตจากการแพ็คจริง (ปิดบังข้อมูลผู้รับแล้ว)";
  }
})();
