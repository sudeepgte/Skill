document.addEventListener('DOMContentLoaded', () => {
    
    // Select all carousels
    const carousels = document.querySelectorAll('.course-carousel-wrapper');

    carousels.forEach(wrapper => {
        const prevBtn = wrapper.querySelector('.prev-btn');
        const nextBtn = wrapper.querySelector('.next-btn');
        const carousel = wrapper.querySelector('.course-carousel');

        if (!carousel) return;

        nextBtn.addEventListener('click', () => {
            const cardWidth = carousel.querySelector('.course-card').offsetWidth;
            carousel.scrollBy({ left: cardWidth * 2, behavior: 'smooth' });
        });

        prevBtn.addEventListener('click', () => {
            const cardWidth = carousel.querySelector('.course-card').offsetWidth;
            carousel.scrollBy({ left: -(cardWidth * 2), behavior: 'smooth' });
        });
    });

    // --- Hero Image Carousel Logic ---
    const track = document.querySelector('.carousel-track');
    if (track) {
        const slides = Array.from(track.children);
        const nextButton = document.querySelector('.carousel-next');
        const prevButton = document.querySelector('.carousel-prev');
        const slideWidth = slides[0].getBoundingClientRect().width;

        // Arrange slides next to one another
        const setSlidePosition = (slide, index) => {
            slide.style.left = slideWidth * index + 'px';
        };
        slides.forEach(setSlidePosition);

        const moveToSlide = (track, currentSlide, targetSlide) => {
            track.style.transform = 'translateX(-' + targetSlide.style.left + ')';
            currentSlide.classList.remove('current-slide');
            targetSlide.classList.add('current-slide');
        }

        // Initialize first slide as current
        slides[0].classList.add('current-slide');

        // Next Button
        nextButton.addEventListener('click', e => {
            const currentSlide = track.querySelector('.current-slide');
            let nextSlide = currentSlide.nextElementSibling;
            if (!nextSlide) {
                nextSlide = slides[0]; // Loop back to start
            }
            moveToSlide(track, currentSlide, nextSlide);
        });

        // Prev Button
        prevButton.addEventListener('click', e => {
            const currentSlide = track.querySelector('.current-slide');
            let prevSlide = currentSlide.previousElementSibling;
            if (!prevSlide) {
                prevSlide = slides[slides.length - 1]; // Loop to end
            }
            moveToSlide(track, currentSlide, prevSlide);
        });

        // Auto-Slide
        setInterval(() => {
            const currentSlide = track.querySelector('.current-slide');
            let nextSlide = currentSlide.nextElementSibling;
            if (!nextSlide) {
                nextSlide = slides[0];
            }
            moveToSlide(track, currentSlide, nextSlide);
        }, 5000); // 5 seconds
    }
