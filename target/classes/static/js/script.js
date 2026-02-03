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

    // Optional: Hero Carousel Auto-play logic could go here
    // For now static hero image as per original simple design, but styled better.
});
