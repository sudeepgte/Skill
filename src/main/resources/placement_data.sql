-- Sample Interview Questions
INSERT INTO interview_questions (category, question, answer) VALUES
('Java', 'What is the difference between JDK, JRE, and JVM?', 'JDK is for development, JRE is for running apps, and JVM is the engine that executes bytecode.'),
('Java', 'Explain oops concepts in Java.', 'Inheritance, Encapsulation, Polymorphism, and Abstraction are the four pillars of OOPs.'),
('MySQL', 'Difference between JOIN and UNION?', 'JOIN combines columns from different tables, UNION combines rows from different tables.'),
('HR', 'Tell me about yourself.', 'Focus on your education, key skills, projects, and career aspirations relevant to the job.'),
('Python', 'What are decorators in Python?', 'Decorators allow you to modify the behavior of a function or class without permanently modifying it.');

-- Sample Aptitude Questions
INSERT INTO aptitude_questions (category, question, optionA, optionB, optionC, optionD, correctOption) VALUES
('Quantitative', 'A train 120m long passes a pole in 6 seconds. What is the speed of the train in km/hr?', '72 km/hr', '60 km/hr', '48 km/hr', '80 km/hr', 'A'),
('Logical Reasoning', 'Point out to a lady, a man said, "Her mother is the only daughter of my mother-in-law." How is the man related to the lady?', 'Father', 'Brother', 'Husband', 'Uncle', 'A'),
('Verbal', 'Choose the correct synonym for "Benevolent".', 'Cruel', 'Kind', 'Selfish', 'Greedy', 'B'),
('Quantitative', 'Work done by A in 10 days and B in 15 days. Together they do it in?', '5 days', '8 days', '6 days', '7 days', 'C');
