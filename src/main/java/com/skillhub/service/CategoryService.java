package com.skillhub.service;

import com.skillhub.model.Category;
import com.skillhub.repository.CategoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class CategoryService {

    @Autowired
    private CategoryRepository categoryRepository;

    private static final Map<String, List<String>> SUBCATEGORIES_MAP = new LinkedHashMap<>();
    private static final Map<String, List<Map<String, Object>>> CATEGORY_FIELDS_MAP = new LinkedHashMap<>();

    static {
        // 1. Development and IT
        SUBCATEGORIES_MAP.put("Development and IT", Arrays.asList(
                "Web Development", "Data Science & AI", "Mobile Development",
                "Cloud & DevOps", "Cybersecurity", "Game Development", "Programming Languages", "Software Engineering"
        ));

        // 2. Business and Marketing
        List<String> bizSubs = Arrays.asList(
                "Digital Marketing", "SEO & Content Marketing", "Social Media Marketing",
                "Entrepreneurship & Startups", "Sales & Negotiation", "Finance & Investing", "Product Management"
        );
        SUBCATEGORIES_MAP.put("Business and Marketing", bizSubs);
        SUBCATEGORIES_MAP.put("Business Marketing", bizSubs);

        // 3. Office Productivity and Design
        SUBCATEGORIES_MAP.put("Office Productivity and Design", Arrays.asList(
                "UI/UX Design", "Graphic Design", "Microsoft Excel & Spreadsheets",
                "Presentation & PowerPoint", "3D Modeling & Animation", "Project Management Tools"
        ));

        // 4. Music
        SUBCATEGORIES_MAP.put("Music", Arrays.asList(
                "Acoustic & Electric Guitar", "Piano & Keyboard", "Vocal Training & Singing",
                "Music Production & DAW", "Music Theory", "Drums & Percussion", "Violin & String Instruments"
        ));

        // 5. Health, Fitness and Yoga
        List<String> fitnessSubs = Arrays.asList(
                "Yoga & Pranayama", "Home Fitness & Calisthenics", "Weight Loss & Cardio",
                "Nutrition & Meal Planning", "Meditation & Mindfulness", "Pilates & Flexibility"
        );
        SUBCATEGORIES_MAP.put("Health, Fitness and Yoga", fitnessSubs);
        SUBCATEGORIES_MAP.put("Health Fitness and Yoga", fitnessSubs);

        // 6. Art, Craft and Painting
        SUBCATEGORIES_MAP.put("Art, Craft and Painting", Arrays.asList(
                "Watercolor Painting", "Oil & Acrylic Painting", "Sketching & Drawing",
                "Digital Illustration", "Crafts & DIY", "Calligraphy & Lettering", "Pottery & Sculpture"
        ));

        // 7. Kids Learning and Speaking
        List<String> kidsSubs = Arrays.asList(
                "Public Speaking for Kids", "Phonics & Early Reading", "Creative Writing & Storytelling",
                "STEM & Coding for Kids", "Fun Math & Logic", "Kids Art & Craft"
        );
        SUBCATEGORIES_MAP.put("Kids Learning and Speaking", kidsSubs);
        SUBCATEGORIES_MAP.put("Kids Learning and Speaking Course", kidsSubs);

        // 8. Languages
        List<String> langSubs = Arrays.asList(
                "English", "Spanish", "French", "German", "Japanese", "Mandarin Chinese", "Hindi", "Italian"
        );
        SUBCATEGORIES_MAP.put("Languages", langSubs);
        SUBCATEGORIES_MAP.put("Language", langSubs);

        // 9. Photography
        SUBCATEGORIES_MAP.put("Photography", Arrays.asList(
                "Portrait Photography", "Landscape & Nature", "Street & Travel",
                "Commercial & Product", "Video & Cinematography", "Photo Editing & Lightroom"
        ));

        // 10. Cooking and Culinary Arts
        List<String> culinarySubs = Arrays.asList(
                "Home Cooking & Everyday Meals", "Baking & Pastry Arts", "Culinary Techniques & Knife Skills",
                "Regional & International Cuisines", "Food Safety & Hygiene", "Mixology & Beverages", "Cake Decorating & Desserts"
        );
        SUBCATEGORIES_MAP.put("Cooking and Culinary Arts", culinarySubs);
        SUBCATEGORIES_MAP.put("Cooking", culinarySubs);
        SUBCATEGORIES_MAP.put("Culinary Arts", culinarySubs);

        // 11. Other
        SUBCATEGORIES_MAP.put("Other", Arrays.asList(
                "Personal Development", "Career Growth", "Hobbies & Lifestyle", "General Studies"
        ));

        // Initialize Category Dynamic Fields
        initCategoryFieldDefinitions();
    }

    private static void initCategoryFieldDefinitions() {
        // 1. Development and IT
        CATEGORY_FIELDS_MAP.put("Development and IT", Arrays.asList(
                field("programmingLanguages", "Programming Languages", "text", "e.g. Java, Python, TypeScript, Go", "Primary programming languages used in the course", true),
                field("frameworksLibrariesTools", "Frameworks, Libraries & Tools", "text", "e.g. Spring Boot, React, Docker, Kubernetes", "Major libraries and developer tools taught", false),
                field("devEnvironmentRequirements", "Development Environment & Tools", "text", "e.g. JDK 17+, VS Code / IntelliJ, Node.js 18+", "Software required to follow along", false),
                fieldWithOpts("osRequirements", "Operating System Compatibility", "select", Arrays.asList("Cross-platform (Windows, macOS, Linux)", "Windows Preferred", "macOS Required", "Linux Required"), "Target operating system", false),
                field("prerequisiteTechnologies", "Prerequisite Technologies", "text", "e.g. Git basics, Basic command line, HTML/CSS", "Technologies students should already be familiar with", false),
                field("repositoryUrl", "Starter Code / GitHub Repository URL", "text", "e.g. https://github.com/skillhub/course-starter", "Optional starter code or reference repository", false),
                field("evaluationCriteria", "Practical Assignments & Evaluation Criteria", "textarea", "Describe coding exercises, project deliverables, and assessment guidelines...", "How student code and practical work will be assessed", false)
        ));

        // 2. Business and Marketing
        List<Map<String, Object>> bizFields = Arrays.asList(
                field("businessDomainIndustry", "Business Domain & Target Industry", "text", "e.g. E-Commerce, B2B SaaS, Retail, Fintech", "The business industry this course focuses on", true),
                field("marketingChannelsStrategies", "Marketing Channels & Strategies", "text", "e.g. SEO, Google Ads, Meta Paid Ads, Email Funnels", "Core acquisition or retention channels taught", false),
                field("businessToolsPlatforms", "Business Platforms & Tools", "text", "e.g. Google Analytics 4, SEMrush, HubSpot, Canva", "Platforms students will use or analyze", false),
                field("caseStudies", "Case Studies Covered", "textarea", "Summarize real-world brand case studies and business examples...", "Real-world cases explored during lectures", false),
                field("businessDeliverables", "Practical Campaign / Project Deliverables", "textarea", "e.g. Comprehensive Go-To-Market Plan, Paid Ad Campaign Structure...", "Actionable outputs students build by course completion", false)
        );
        CATEGORY_FIELDS_MAP.put("Business and Marketing", bizFields);
        CATEGORY_FIELDS_MAP.put("Business Marketing", bizFields);

        // 3. Office Productivity and Design
        CATEGORY_FIELDS_MAP.put("Office Productivity and Design", Arrays.asList(
                field("designSoftwareTaught", "Software & Tools Taught", "text", "e.g. Figma, Adobe Photoshop, Microsoft Excel, Notion", "Applications covered in the curriculum", true),
                fieldWithOpts("designDiscipline", "Design & Productivity Discipline", "select", Arrays.asList("UI/UX Design", "Graphic Design & Branding", "Advanced Spreadsheets & Modeling", "Document & Presentation Design", "Workflow Automation"), "Primary discipline focus", false),
                field("requiredSoftwareVersions", "Required Software Versions", "text", "e.g. Figma (Free Web Tier), Excel 2019+ or Microsoft 365", "Software version requirements", false),
                field("templatesProvided", "Templates & Practice Assets Provided", "textarea", "e.g. UI Component Kit, Financial Modeling Workbooks, Pitch Deck Templates...", "Ready-to-use downloadable assets included", false),
                field("practicalDeliverables", "Practical Projects & Portfolio Deliverables", "textarea", "Describe the design or spreadsheet projects students will complete...", "Deliverables for client presentations or student portfolios", false)
        ));

        // 4. Music
        CATEGORY_FIELDS_MAP.put("Music", Arrays.asList(
                field("musicInstrumentDiscipline", "Instrument or Discipline", "text", "e.g. Acoustic Guitar, Grand Piano, Vocals, Electronic Production", "Primary instrument or music craft", true),
                field("musicGenreTechnique", "Genre & Playing Technique", "text", "e.g. Fingerstyle, Classical, Blues Improvisation, EDM Sound Design", "Stylistic focus of the course", false),
                fieldWithOpts("theoryPracticalLevel", "Theory vs. Practical Balance", "select", Arrays.asList("Hands-on Playing Focus (Minimal Theory)", "Balanced Theory & Technique", "Advanced Music Theory & Composition"), "Pedagogical focus", false),
                field("requiredInstrumentsEquipment", "Required Instruments & Equipment", "text", "e.g. 6-string guitar, Capo, Tuner, DAW software (optional)", "Physical instruments and equipment needed", false),
                field("sheetMusicAudioTracks", "Audio Tracks & Sheet Music Included", "text", "e.g. Backing MP3 practice tracks, PDF tabs and standard notation", "Practice accompaniment provided", false),
                field("musicPracticeAssignments", "Weekly Practice Assignments", "textarea", "Describe daily or weekly practice routines, scales, and song milestones...", "Expectations for self-guided practice", false)
        ));

        // 5. Health, Fitness and Yoga
        List<Map<String, Object>> fitFields = Arrays.asList(
                field("fitnessDiscipline", "Activity / Discipline", "text", "e.g. Hatha Yoga, Calisthenics, HIIT Fat Loss, Breathwork", "Physical discipline or wellness practice", true),
                fieldWithOpts("fitnessExperienceLevel", "Physical Experience Level", "select", Arrays.asList("All Fitness Levels Welcome", "Beginner / Gentle", "Intermediate Intensity", "Advanced Athletic"), "Intensity level for safety", false),
                field("fitnessEquipmentRequirements", "Required Equipment & Props", "text", "e.g. Yoga mat, Resistance bands, Pull-up bar, No equipment needed", "Physical gear students should have on hand", false),
                field("sessionStructure", "Workout / Session Structure", "text", "e.g. 5m Warm-up -> 30m Core Workout -> 10m Cool-down & Mobility", "Typical flow of each practice video", false),
                field("safetyGuidance", "Safety Precautions & Modifications", "textarea", "Provide posture modifications for injury prevention, joint sensitivity, or back care...", "Critical safety and alignment instructions", false),
                field("instructorCredentials", "Instructor Credentials & Certifications", "text", "e.g. RYT-500 Yoga Alliance Certified, NASM-CPT", "Professional qualifications in the discipline", false)
        );
        CATEGORY_FIELDS_MAP.put("Health, Fitness and Yoga", fitFields);
        CATEGORY_FIELDS_MAP.put("Health Fitness and Yoga", fitFields);

        // 6. Art, Craft and Painting
        CATEGORY_FIELDS_MAP.put("Art, Craft and Painting", Arrays.asList(
                field("artFormTechnique", "Art Medium & Technique", "text", "e.g. Wet-on-wet Watercolor, Acrylic Impasto, Graphite Realism, Resin Art", "Specific artistic medium", true),
                field("materialsEquipment", "Materials & Art Supplies List", "textarea", "List paper weights, brush shapes, pigments, and palettes students need...", "Comprehensive supply checklist", false),
                field("stepByStepProjects", "Step-by-Step Projects", "textarea", "Detail the progressive artworks or craft pieces created across the course...", "Art pieces students will complete", false),
                field("referenceTemplates", "Reference Images & Outlines Included", "text", "e.g. Printable line-art stencils and high-res source photos included", "Reference materials supplied", false)
        ));

        // 7. Kids Learning and Speaking
        List<Map<String, Object>> kidsFields = Arrays.asList(
                fieldWithOpts("kidsAgeGroup", "Target Age Group", "select", Arrays.asList("Ages 4 - 6 (Early Learners)", "Ages 7 - 9 (Elementary)", "Ages 10 - 12 (Pre-Teens)", "Teens (13 - 16)"), "Age range designed for cognitive appropriateness", true),
                field("kidsSubjectArea", "Learning Area & Topic", "text", "e.g. Public Speaking & Confidence, Phonics & Storytelling, Junior Coding", "Focus topic for kids", true),
                fieldWithOpts("kidsProficiencyLevel", "Speaking / Skill Level", "select", Arrays.asList("Beginner / Shy Speaker", "Eager Explorer", "Confident Speaker Level-Up"), "Initial level of student", false),
                field("kidsLearningObjectives", "Age-Appropriate Learning Milestones", "textarea", "e.g. Deliver 1-minute self-introduction, Express opinions with confidence, Master 40 new sight words...", "Milestones children will achieve", false),
                field("kidsRequiredMaterials", "Supplies & Parent Involvement", "text", "e.g. Markers, paper, toy props for show-and-tell. Parental presence optional.", "Household items needed", false),
                field("kidsInteractiveActivities", "Fun Activities & Challenges", "textarea", "Describe games, tongue-twisters, puppet plays, or weekly mini-speech videos...", "Engagement methods used", false)
        );
        CATEGORY_FIELDS_MAP.put("Kids Learning and Speaking", kidsFields);
        CATEGORY_FIELDS_MAP.put("Kids Learning and Speaking Course", kidsFields);

        // 8. Languages
        List<Map<String, Object>> langFields = Arrays.asList(
                field("sourceTargetLanguage", "Target & Instruction Languages", "text", "e.g. Target: Spanish | Instruction Language: English", "Language being taught and medium of instruction", true),
                fieldWithOpts("languageProficiencyLevel", "Target CEFR Level", "select", Arrays.asList("A1 - Complete Beginner", "A2 - Elementary", "B1 - Intermediate", "B2 - Upper Intermediate", "C1 - Advanced Fluency"), "Standard language proficiency target", false),
                field("languageFocusAreas", "Core Focus Areas", "text", "e.g. Everyday Speaking, Accent Training, Grammar & Verbs, Listening Comprehension", "Emphasis of curriculum", false),
                field("languagePracticeResources", "Practice Dialogues & Flashcards", "textarea", "Detail downloadable audio dialogues, Anki flashcard decks, and vocabulary sheets...", "Resources to build retention", false),
                fieldWithOpts("languageAssessmentMethod", "Assessment & Speaking Practice Method", "select", Arrays.asList("Interactive Dialogue Role-Play", "Grammar & Listening Quizzes", "Speaking Video Submissions"), "How student progress is verified", false)
        );
        CATEGORY_FIELDS_MAP.put("Languages", langFields);
        CATEGORY_FIELDS_MAP.put("Language", langFields);

        // 9. Photography
        CATEGORY_FIELDS_MAP.put("Photography", Arrays.asList(
                fieldWithOpts("photographyGenre", "Photography Genre", "select", Arrays.asList("Portrait & People", "Landscape & Travel", "Street & Documentary", "Product & Food Commercial", "Wildlife & Action", "General Photography Fundamentals"), "Primary photography style", true),
                field("cameraEquipmentRequirements", "Camera & Lens Requirements", "text", "e.g. Any DSLR or Mirrorless Camera with manual mode (Smartphones welcome)", "Gear required to execute assignments", false),
                field("editingSoftwareTools", "Post-Processing Software", "text", "e.g. Adobe Lightroom Classic, Photoshop, Snapseed", "Editing tools demonstrated", false),
                field("practicalPhotoAssignments", "Shooting Assignments", "textarea", "Describe hands-on photo shoot prompts (e.g. Golden hour portrait, Motion blur street shot)...", "Practical shoot challenges", false),
                field("photoDeliverables", "Final Portfolio Deliverables", "textarea", "e.g. 5 curated photographs with RAW file adjustments and composition analysis...", "Final work to submit or showcase", false)
        ));

        // 10. Other
        CATEGORY_FIELDS_MAP.put("Other", Arrays.asList(
                field("customDomain", "Course Subject Domain", "text", "e.g. Specialized Skill, Professional Trade, General Interest", "The domain of this unique course", true),
                field("customObjectives", "Course-Specific Learning Goals", "textarea", "Detail the distinct outcomes and competencies learners will achieve...", "Specific objectives", true),
                field("customPrerequisites", "Relevant Prerequisites & Background", "text", "e.g. Any background knowledge or equipment required", "Prerequisites", false),
                field("customAdditionalInfo", "Additional Instructions & Methodology", "textarea", "Provide any additional information, materials, or learning methodology...", "Additional course details", false)
        ));
    }

    private static Map<String, Object> field(String key, String label, String type, String placeholder, String description, boolean required) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("key", key);
        f.put("label", label);
        f.put("type", type);
        f.put("placeholder", placeholder);
        f.put("description", description);
        f.put("required", required);
        return f;
    }

    private static Map<String, Object> fieldWithOpts(String key, String label, String type, List<String> options, String description, boolean required) {
        Map<String, Object> f = field(key, label, type, "", description, required);
        f.put("options", options);
        return f;
    }

    public List<Category> getAllCategories() {
        return categoryRepository.findAll();
    }

    public Category getCategoryById(Long id) {
        return categoryRepository.findById(id).orElse(null);
    }

    public Category getCategoryByName(String name) {
        return categoryRepository.findByName(name);
    }

    public void saveCategory(Category category) {
        categoryRepository.save(category);
    }

    public List<Category> getRootCategories() {
        return categoryRepository.findByParentIsNull();
    }

    public void deleteCategory(Long id) {
        categoryRepository.deleteById(id);
    }

    public List<Category> searchCategories(String query) {
        return categoryRepository.findByNameContainingIgnoreCase(query);
    }

    public Map<String, List<String>> getCategorySubcategoriesMap() {
        return Collections.unmodifiableMap(SUBCATEGORIES_MAP);
    }

    public List<String> getSubcategoriesForCategory(String categoryName) {
        if (categoryName == null) return Collections.emptyList();
        String trimmed = categoryName.trim();
        List<String> list = SUBCATEGORIES_MAP.get(trimmed);
        if (list != null) return list;
        for (Map.Entry<String, List<String>> entry : SUBCATEGORIES_MAP.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(trimmed)) {
                return entry.getValue();
            }
        }
        return Collections.emptyList();
    }

    public Map<String, List<Map<String, Object>>> getAllCategoryFieldDefinitions() {
        return Collections.unmodifiableMap(CATEGORY_FIELDS_MAP);
    }

    public List<Map<String, Object>> getCategoryFieldDefinitions(String categoryName) {
        if (categoryName == null) return Collections.emptyList();
        String trimmed = categoryName.trim();
        List<Map<String, Object>> fields = CATEGORY_FIELDS_MAP.get(trimmed);
        if (fields != null) return fields;
        for (Map.Entry<String, List<Map<String, Object>>> entry : CATEGORY_FIELDS_MAP.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(trimmed)) {
                return entry.getValue();
            }
        }
        return CATEGORY_FIELDS_MAP.getOrDefault("Other", Collections.emptyList());
    }
}
