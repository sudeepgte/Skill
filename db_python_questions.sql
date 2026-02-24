USE SkillHubDB;

INSERT INTO exam_questions (course_id, question_text, optiona, optionb, optionc, optiond, correct_option) VALUES
(23, '1. What is the output of print(2 ** 3)?', '6', '8', '9', '12', 'B'),
(23, '2. Which of the following is used to define a block of code in Python?', 'Brackets', 'Indentation', 'Key', 'Catch', 'B'),
(23, '3. What is the correct extension of the Python file?', '.python', '.pl', '.py', '.p', 'C'),
(23, '4. Which keyword is used for function in Python?', 'Function', 'def', 'Fun', 'Define', 'B'),
(23, '5. Which of the following character is used to give single-line comments in Python?', '//', '#', '!', '/*', 'B'),
(23, '6. What will be the output of type(type(int))?', 'class type', 'class int', 'type int', 'Error', 'A'),
(23, '7. What is the method inside the class in python language?', 'Object', 'Function', 'Attribute', 'Argument', 'B'),
(23, '8. Which of the following declarations is incorrect?', '_x = 2', '__x = 3', '__xyz__ = 5', 'None of these', 'D'),
(23, '9. Why does the name of local variables start with an underscore discouraged?', 'To identify the variable', 'It confuses the interpreter', 'It indicates a private variable of a class', 'None of these', 'C'),
(23, '10. Which of the following is not a core data type in Python?', 'Tuples', 'Lists', 'Class', 'Dictionary', 'C'),
(23, '11. What is the output of print(10 // 3)?', '3.33', '3', '3.0', '0', 'B'),
(23, '12. Which function is used to get the length of a list?', 'size()', 'length()', 'len()', 'count()', 'C'),
(23, '13. In Python, what is a Tuple?', 'A mutable sequence of items', 'An immutable sequence of items', 'A set of unique items', 'A dictionary of key-value pairs', 'B'),
(23, '14. How do you insert an element at a specific index in a list?', 'list.add(index, element)', 'list.insert(index, element)', 'list.append(element)', 'list.push(index, element)', 'B'),
(23, '15. Which operator is used for string concatenation?', '+', '&', '*', '-', 'A'),
(23, '16. How do you create a dictionary in Python?', '{}', '[]', '()', '<>', 'A'),
(23, '17. What does the break statement do in a loop?', 'Continues to the next iteration', 'Terminates the loop entirely', 'Pauses the loop', 'Skips the current block', 'B'),
(23, '18. What is a lambda function in Python?', 'An anonymous function', 'A built-in function', 'A recursive function', 'A predefined mathematical function', 'A'),
(23, '19. Which module is used to work with regular expressions in Python?', 'regex', 're', 'reg', 'expressions', 'B'),
(23, '20. What is the purpose of the self keyword in Python classes?', 'It points to the current instance of the class', 'It defines a private method', 'It is a reserved keyword for inheritance', 'It represents the base class', 'A');
