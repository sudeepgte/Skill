document.addEventListener('DOMContentLoaded', function () {

    /* ── CAROUSEL ── */
    const track = document.getElementById('heroTrack');
    const dotsWrap = document.getElementById('heroDots');
    const slides = track ? [...track.querySelectorAll('.hero-slide')] : [];
    let cur = 0, timer;

    function goTo(n) {
        if (!slides.length || !track) return;
        cur = (n + slides.length) % slides.length;
        track.style.transform = `translateX(-${cur * 100}%)`;
        document.querySelectorAll('.hero-dot').forEach((d, i) =>
            d.classList.toggle('active', i === cur));
    }
    function resetTimer() { 
        clearInterval(timer); 
        if (slides.length > 1) {
            timer = setInterval(() => goTo(cur + 1), 5500); 
        }
    }

    if (slides.length) {
        slides.forEach((_, i) => {
            const d = document.createElement('button');
            d.className = 'hero-dot' + (i === 0 ? ' active' : '');
            d.setAttribute('type', 'button');
            d.setAttribute('aria-label', 'Slide ' + (i + 1));
            d.onclick = () => { goTo(i); resetTimer(); };
            if (dotsWrap) {
                dotsWrap.appendChild(d);
            }
        });
        const prevBtn = document.getElementById('heroPrev');
        if (prevBtn) {
            prevBtn.onclick = () => { goTo(cur - 1); resetTimer(); };
        }
        const nextBtn = document.getElementById('heroNext');
        if (nextBtn) {
            nextBtn.onclick = () => { goTo(cur + 1); resetTimer(); };
        }
        goTo(0); 
        resetTimer();
    }

    /* ── AUTO-SCROLL TO COURSES SECTION ── */
    // When user clicks a category tab or navbar category, the page reloads
    // with categoryId or query in the URL. We detect this and smoothly scroll
    // to the #courses section so the filtered results are immediately visible.
    const params = new URLSearchParams(window.location.search);
    const coursesSection = document.getElementById('courses');
    if (coursesSection && (params.has('categoryId') || params.has('query'))) {
        // Small delay to let the page fully render before scrolling
        setTimeout(() => {
            coursesSection.scrollIntoView({ behavior: 'smooth', block: 'start' });
        }, 120);
    }

});
