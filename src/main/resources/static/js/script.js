document.addEventListener('DOMContentLoaded', () => {

    // --- Hero Carousel Logic (Drag + Swipe + Dots) ---
    const heroTrack = document.getElementById('heroTrack');
    const heroContainer = document.querySelector('.hero-carousel-container');

    if (heroTrack && heroContainer) {
        const slides = Array.from(heroTrack.children);
        const nextBtn = document.getElementById('heroNext');
        const prevBtn = document.getElementById('heroPrev');
        const dotsNav = document.getElementById('heroDots');

        let currentIndex = 0;
        let startPos = 0;
        let currentTranslate = 0;
        let prevTranslate = 0;
        let isDragging = false;
        let animationID;
        let autoPlayInterval;

        // Create Dots
        slides.forEach((_, index) => {
            const dot = document.createElement('div');
            dot.classList.add('hero-dot');
            if (index === 0) dot.classList.add('active');
            dot.addEventListener('click', () => {
                setPositionByIndex(index);
                resetAutoPlay();
            });
            dotsNav.appendChild(dot);
        });
        const dots = Array.from(dotsNav.children);

        // Events
        slides.forEach((slide, index) => {
            // Prevent default image drag
            const img = slide.querySelector('img');
            if (img) img.addEventListener('dragstart', (e) => e.preventDefault());

            // Touch events
            slide.addEventListener('touchstart', touchStart(index));
            slide.addEventListener('touchend', touchEnd);
            slide.addEventListener('touchmove', touchMove);

            // Mouse events
            slide.addEventListener('mousedown', touchStart(index));
            slide.addEventListener('mouseup', touchEnd);
            slide.addEventListener('mouseleave', () => {
                if (isDragging) touchEnd();
            });
            slide.addEventListener('mousemove', touchMove);
        });

        // Disable context menu on right click
        window.oncontextmenu = function (event) {
            event.preventDefault();
            event.stopPropagation();
            return false;
        };

        function touchStart(index) {
            return function (event) {
                isDragging = true;
                currentIndex = index; // Optional: track where we started
                startPos = getPositionX(event);
                animationID = requestAnimationFrame(animation);
                heroTrack.classList.add('active');
                clearInterval(autoPlayInterval); // Stop auto-play on interaction
            }
        }

        function touchMove(event) {
            if (isDragging) {
                const currentPosition = getPositionX(event);
                currentTranslate = prevTranslate + currentPosition - startPos;
            }
        }

        function touchEnd() {
            isDragging = false;
            cancelAnimationFrame(animationID);
            heroTrack.classList.remove('active');

            const movedBy = currentTranslate - prevTranslate;

            // Threshold to change slide
            if (movedBy < -100 && currentIndex < slides.length - 1) currentIndex += 1;
            if (movedBy > 100 && currentIndex > 0) currentIndex -= 1;

            setPositionByIndex(currentIndex);
            resetAutoPlay();
        }

        function getPositionX(event) {
            return event.type.includes('mouse') ? event.pageX : event.touches[0].clientX;
        }

        function animation() {
            setSliderPosition();
            if (isDragging) requestAnimationFrame(animation);
        }

        function setSliderPosition() {
            heroTrack.style.transform = `translateX(${currentTranslate}px)`;
        }

        function setPositionByIndex(index) {
            currentIndex = index;
            // Limit bounds just in case
            if (currentIndex < 0) currentIndex = 0;
            if (currentIndex > slides.length - 1) currentIndex = slides.length - 1;

            currentTranslate = currentIndex * -heroContainer.offsetWidth; // Assuming 100% width
            prevTranslate = currentTranslate;
            setSliderPosition();
            updateDots(currentIndex);
        }

        function updateDots(index) {
            dots.forEach(dot => dot.classList.remove('active'));
            if (dots[index]) dots[index].classList.add('active');
        }

        // Buttons
        if (nextBtn) nextBtn.addEventListener('click', () => {
            if (currentIndex < slides.length - 1) setPositionByIndex(currentIndex + 1);
            else setPositionByIndex(0); // Loop
            resetAutoPlay();
        });

        if (prevBtn) prevBtn.addEventListener('click', () => {
            if (currentIndex > 0) setPositionByIndex(currentIndex - 1);
            else setPositionByIndex(slides.length - 1); // Loop
            resetAutoPlay();
        });

        // Resize handling
        window.addEventListener('resize', () => {
            setPositionByIndex(currentIndex);
        });

        // Auto Play
        function startAutoPlay() {
            autoPlayInterval = setInterval(() => {
                if (currentIndex < slides.length - 1) setPositionByIndex(currentIndex + 1);
                else setPositionByIndex(0);
            }, 3500);
        }

        function resetAutoPlay() {
            clearInterval(autoPlayInterval);
            startAutoPlay();
        }

        startAutoPlay();
    }

    // --- Course Carousel Logic (Simple Scroll) ---
    // (Already in main_index.html, but if we want to move it here later we can. 
    //  For now, let's keep this file clean for the Hero or general helpers)

});
