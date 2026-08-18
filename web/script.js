/**
 * Metrolist Landing Page JavaScript
 */

document.addEventListener('DOMContentLoaded', () => {
  initMobileNav();
  initScreenshotShowcase();
  initFaqAccordion();
  fetchGitHubReleaseInfo();
});

/**
 * Mobile Navigation Drawer Toggle
 */
function initMobileNav() {
  const toggleBtn = document.getElementById('nav-toggle');
  const navLinks = document.getElementById('nav-links');

  if (toggleBtn && navLinks) {
    toggleBtn.addEventListener('click', () => {
      navLinks.classList.toggle('active');
    });

    // Close menu when clicking outside or link
    document.querySelectorAll('.nav-link').forEach(link => {
      link.addEventListener('click', () => {
        navLinks.classList.remove('active');
      });
    });
  }
}

/**
 * Screenshot Showcase Tab Switcher
 */
function initScreenshotShowcase() {
  const tabBtns = document.querySelectorAll('.tab-btn');
  const showcaseImg = document.getElementById('showcase-img');

  if (!tabBtns.length || !showcaseImg) return;

  const screenshots = {
    home: 'assets/screenshot_1.png',
    artist: 'assets/screenshot_2.png',
    recognize: 'assets/screenshot_3.png',
    social: 'assets/screenshot_4.png',
    player: 'assets/screenshot_5.png',
    lyrics: 'assets/screenshot_6.png'
  };

  tabBtns.forEach(btn => {
    btn.addEventListener('click', () => {
      const target = btn.dataset.target;
      if (!screenshots[target]) return;

      // Update active state
      tabBtns.forEach(b => b.classList.remove('active'));
      btn.classList.add('active');

      // Animate transition
      showcaseImg.style.opacity = '0';
      showcaseImg.style.transform = 'scale(0.97)';

      setTimeout(() => {
        showcaseImg.src = screenshots[target];
        showcaseImg.style.opacity = '1';
        showcaseImg.style.transform = 'scale(1)';
      }, 150);
    });
  });
}

/**
 * FAQ Accordion Toggle
 */
function initFaqAccordion() {
  const faqQuestions = document.querySelectorAll('.faq-question');

  faqQuestions.forEach(question => {
    question.addEventListener('click', () => {
      const faqItem = question.parentElement;
      const isActive = faqItem.classList.contains('active');

      // Close all other items
      document.querySelectorAll('.faq-item').forEach(item => {
        item.classList.remove('active');
      });

      // Toggle clicked item
      if (!isActive) {
        faqItem.classList.add('active');
      }
    });
  });
}

/**
 * Fetch GitHub Latest Release Tag & Stats
 */
async function fetchGitHubReleaseInfo() {
  const releaseVersionEl = document.getElementById('release-version');
  if (!releaseVersionEl) return;

  try {
    const response = await fetch('https://api.github.com/repos/MetrolistGroup/Metrolist/releases/latest');
    if (response.ok) {
      const data = await response.json();
      if (data.tag_name) {
        releaseVersionEl.textContent = `APK ${data.tag_name}`;
      }
    }
  } catch (error) {
    console.log('GitHub API fetch deferred or offline mode', error);
  }
}
